package de.unikassel.assistant.programme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.unikassel.assistant.i18n.Translator;
import de.unikassel.assistant.ingest.PageFetcher;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.ProgrammeParser.ModuleRef;
import de.unikassel.assistant.programme.ProgrammeParser.Plan;
import de.unikassel.assistant.programme.ProgrammeProperties.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Imports degree programmes from the university's module database and keeps them as snapshots:
 * the regulations page for areas and rules, then one request per module for when it is taught,
 * its exam and prerequisites. Polite like the crawler (same fetcher, same delay).
 */
@Service
public class ProgrammeService {

	private static final Logger log = LoggerFactory.getLogger(ProgrammeService.class);

	private final ProgrammeProperties config;

	private final PageFetcher fetcher;

	private final ProgrammeParser parser;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final Translator translator;

	private final Map<String, Programme> cache = new ConcurrentHashMap<>();

	private final AtomicBoolean running = new AtomicBoolean();

	private volatile Map<String, ModuleHistory> history = Map.of();

	ProgrammeService(ProgrammeProperties config, PageFetcher fetcher, ProgrammeParser parser, JdbcClient jdbc,
			JsonMapper json, Translator translator) {
		this.config = config;
		this.fetcher = fetcher;
		this.parser = parser;
		this.jdbc = jdbc;
		this.json = json;
		this.translator = translator;
	}

	public List<Source> sources() {
		return this.config.programmes();
	}

	/** The stored programme, or empty if it has not been imported yet. */
	public Optional<Programme> programme(String id) {
		Programme cached = this.cache.get(id);
		if (cached != null) {
			return Optional.of(cached);
		}
		Optional<Programme> stored = this.jdbc.sql("SELECT data FROM programme_snapshot WHERE id = ?")
			.param(id)
			.query(String.class)
			.optional()
			.map((data) -> this.json.readValue(data, Programme.class));
		stored.ifPresent((p) -> this.cache.put(id, p));
		return stored;
	}

	/** The programme a student's profile points to, if the planner supports it. */
	public Optional<Source> sourceFor(String department, String programmeName) {
		return this.config.programmes()
			.stream()
			.filter((s) -> department == null || department.equals(s.department()))
			.filter((s) -> programmeName != null && s.names().stream().anyMatch((n) -> n.equalsIgnoreCase(programmeName.strip())))
			.findFirst();
	}

	/** Past offerings and results per module code; empty until the university's data is configured. */
	public Map<String, ModuleHistory> history() {
		return this.history;
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		loadHistory();
		boolean missing = this.config.programmes().stream().anyMatch((s) -> programme(s.id()).isEmpty());
		if (missing) {
			startImport();
		}
		this.config.programmes().forEach((s) -> programme(s.id()).ifPresent(this::requestTranslations));
	}

	@Scheduled(cron = "${assistant.planner.cron}")
	void scheduledImport() {
		startImport();
	}

	/** Starts an import of all configured programmes in the background. Returns false if one is running. */
	public boolean startImport() {
		if (!this.running.compareAndSet(false, true)) {
			return false;
		}
		Thread.ofVirtual().name("programme-import").start(() -> {
			try {
				loadHistory();
				for (Source source : this.config.programmes()) {
					importProgramme(source);
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			catch (Exception ex) {
				log.warn("Programme import failed: {}", ex.toString());
			}
			finally {
				this.running.set(false);
			}
		});
		return true;
	}

	private void importProgramme(Source source) throws IOException, InterruptedException {
		var page = this.fetcher.fetch(source.url());
		if (page.isEmpty()) {
			log.warn("Programme {}: regulations page not available", source.id());
			return;
		}
		Plan plan = this.parser.plan(new String(page.get().body(), StandardCharsets.UTF_8), page.get().url());
		if (plan.groups().isEmpty()) {
			log.warn("Programme {}: no study plan found on {}; keeping the previous snapshot", source.id(), source.url());
			return;
		}
		Map<String, Module> modules = new LinkedHashMap<>();
		for (ModuleRef ref : plan.modules().values()) {
			var modulePage = this.fetcher.fetch(ref.url());
			Module module = modulePage.isPresent()
					? this.parser.module(new String(modulePage.get().body(), StandardCharsets.UTF_8), ref)
					: new Module(ref.code(), ref.title(), ref.credits(), Programme.Offered.UNKNOWN, "", "", 1, false, "",
							"", ref.url());
			modules.put(ref.code(), module);
		}
		var rules = plan.rules();
		Programme programme = new Programme(source.id(), source.title(), rules.totalCredits(), rules.regularSemesters(),
				rules.thesisCredits(), rules.thesisMinCredits(), rules.thesisWeeks(), source.url(), Instant.now(),
				plan.groups(), modules);
		this.jdbc.sql("""
				INSERT INTO programme_snapshot (id, source_url, fetched_at, data) VALUES (?, ?, ?, CAST(? AS JSONB))
				ON CONFLICT (id) DO UPDATE SET source_url = excluded.source_url, fetched_at = excluded.fetched_at, data = excluded.data
				""")
			.params(source.id(), source.url(), OffsetDateTime.now(ZoneOffset.UTC), this.json.writeValueAsString(programme))
			.update();
		this.cache.put(source.id(), programme);
		requestTranslations(programme);
		long unknown = modules.values().stream().filter((m) -> m.offered() == Programme.Offered.UNKNOWN).count();
		log.info("Programme {}: {} areas, {} modules ({} without a known semester)", source.id(), plan.groups().size(),
				modules.size(), unknown);
	}

	/** Module and area names are German on the university's pages; English versions are made in the background. */
	private void requestTranslations(Programme programme) {
		List<String> titles = new java.util.ArrayList<>();
		programme.groups().forEach((g) -> titles.add(g.title()));
		programme.modules().values().forEach((m) -> titles.add(m.title()));
		this.translator.request(titles);
	}

	private void loadHistory() {
		try {
			this.history = ModuleHistory.load(this.config.historyFile());
			if (!this.history.isEmpty()) {
				log.info("Loaded past data for {} modules", this.history.size());
			}
		}
		catch (IOException ex) {
			log.warn("Could not read past module data {}: {}", this.config.historyFile(), ex.toString());
		}
	}

}
