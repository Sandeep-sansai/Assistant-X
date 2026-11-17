package de.unikassel.assistant.i18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unikassel.assistant.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * English versions of short German texts from university pages: module and area names from the
 * regulations and news headlines. The university publishes most of these only in German; students
 * who chose English should not see a mix (docs/decisions.md #19).
 *
 * <p>Fixed names (areas and focus options of the regulations) come from a reviewed glossary; the
 * rest is translated once by the language model and stored. Lookups never wait for the model:
 * missing texts are queued and show in German until their translation is ready.
 */
@Service
public class Translator {

	private static final Logger log = LoggerFactory.getLogger(Translator.class);

	/** Titles per model call; small enough for a 7B model to keep the numbering. */
	static final int BATCH = 15;

	/** Reviewed names from the Computer Science M.Sc. regulations, exact matches. */
	static final Map<String, String> GLOSSARY = Map.ofEntries(
			Map.entry("Wahlpflicht Praktische Informatik", "Applied Computer Science"),
			Map.entry("Wahlpflicht Technische Informatik", "Computer Engineering"),
			Map.entry("Wahlpflicht Theoretische Informatik", "Theoretical Computer Science"),
			Map.entry("Wahlpflicht Mathematik/Elektrotechnik", "Mathematics / Electrical Engineering"),
			Map.entry("Wahlpflicht Praktische Informatik, Technische Informatik, Theoretische Informatik oder Mathematik/Elektrotechnik",
					"Free elective (any area)"),
			Map.entry("Projektarbeit", "Project"),
			Map.entry("Seminar", "Seminar"),
			Map.entry("Masterabschlussmodul", "Master's thesis"),
			Map.entry("Schwerpunkt Computational Intelligence & Data Analytics (CIDA)",
					"Computational Intelligence & Data Analytics (CIDA)"),
			Map.entry("Schwerpunkt Energieinfomatik (EInf)", "Energy Informatics (EInf)"),
			Map.entry("Schwerpunkt Energieinformatik (EInf)", "Energy Informatics (EInf)"),
			Map.entry("Schwerpunkt Informatik für die digitale Gesellschaft (IDG)",
					"Computer Science for the Digital Society (IDG)"),
			Map.entry("Schwerpunkt Softwareentwicklung (SW)", "Software Engineering (SW)"),
			Map.entry("Schwerpunkt Umweltinformatik (UW)", "Environmental Informatics (UW)"),
			Map.entry("Freier Schwerpunkt", "Free focus"),
			Map.entry("Pflichtmodul (EInf)", "Compulsory module (EInf)"),
			Map.entry("Pflichtmodule (SW)", "Compulsory modules (SW)"),
			Map.entry("Wahlpflichtmodule (SW)", "Electives (SW)"),
			Map.entry("Wahlpflichtmodule Energietechnik/Energiewirtschaft", "Electives: energy engineering & economics"),
			Map.entry("Wahlpflichtmodule Informatik", "Electives: computer science"),
			Map.entry("Pflichtmodule Umwelt", "Compulsory modules: environment"),
			Map.entry("Wahlpflichtmodule Umwelt", "Electives: environment"),
			Map.entry("Grundlegende Kurse (IDG)", "Foundational courses (IDG)"),
			Map.entry("Fortgeschrittene Kurse (IDG)", "Advanced courses (IDG)"),
			Map.entry("Bereich A: Daten, intelligente Systeme, Kontexte", "Area A: data, intelligent systems, contexts"),
			Map.entry("Bereich B: Design, Interaktion und Gesellschaft", "Area B: design, interaction and society"),
			Map.entry("Foundational Courses", "Foundational Courses"),
			Map.entry("Advanced Courses", "Advanced Courses"),
			Map.entry("Lab Courses", "Lab Courses"),
			Map.entry("Fachbereich Elektrotechnik/Informatik", "Electrical Engineering / Computer Science"));

	private static final String SYSTEM = """
			You translate short German titles from a German university (module names, study areas, news \
			headlines) into natural English, as the university would name them in English.
			Rules:
			- Reply with exactly one line per title, in the same order, each starting with its number: "1. ...".
			- If a title is already English, repeat it unchanged.
			- Use title case like the original: "Functional Programming", not "Functional programming".
			- Keep module codes, abbreviations, numbers, roman numerals, names of people and places unchanged.
			- No explanations, no quotes, nothing else.""";

	/** Short words that only occur in German titles. */
	private static final Set<String> GERMAN_WORDS = Set.of("von", "zu", "zum", "zur", "und", "der", "die", "das", "des",
			"dem", "den", "für", "mit", "im", "am", "auf", "aus", "bei", "über", "ein", "eine", "einer", "wie", "ist");

	/** "Vortrag von Jane Doe zu "Graph Neural Networks"": the department's usual talk announcement. */
	private static final Pattern TALK = Pattern.compile("^Vortrag von (.+?) (?:zu|zum Thema|über):? ([\"„].+)$");

	private static final Pattern LAB = Pattern.compile("^Labor (\\p{Lu}.+)$");

	private static final Pattern LINE = Pattern.compile("^\\s*(\\d+)[.)]\\s*(.+?)\\s*$", Pattern.MULTILINE);

	private final LlmClient llm;

	private final JdbcClient jdbc;

	private final Map<String, String> english = new ConcurrentHashMap<>();

	private final Set<String> queue = ConcurrentHashMap.newKeySet();

	private final AtomicBoolean working = new AtomicBoolean();

	private volatile boolean loaded;

	Translator(LlmClient llm, JdbcClient jdbc) {
		this.llm = llm;
		this.jdbc = jdbc;
	}

	/** The English version if one is known; never calls the model. */
	public Optional<String> english(String german) {
		if (german == null || german.isBlank()) {
			return Optional.empty();
		}
		String fixed = fixed(german.strip());
		if (fixed != null) {
			return Optional.of(fixed);
		}
		load();
		return Optional.ofNullable(this.english.get(german.strip()));
	}

	/**
	 * Known English versions of the given texts (original → English). Texts without one are queued
	 * for translation in the background.
	 */
	public Map<String, String> english(Collection<String> texts) {
		Map<String, String> known = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (String text : texts) {
			if (text == null || text.isBlank()) {
				continue;
			}
			english(text).ifPresentOrElse((en) -> known.put(text, en), () -> missing.add(text.strip()));
		}
		if (!missing.isEmpty()) {
			request(missing);
		}
		return known;
	}

	/** Queues texts for translation in the background. */
	public void request(Collection<String> texts) {
		load();
		for (String text : texts) {
			if (text != null && !text.isBlank() && fixed(text.strip()) == null && !this.english.containsKey(text.strip())) {
				this.queue.add(text.strip());
			}
		}
		if (!this.queue.isEmpty() && this.working.compareAndSet(false, true)) {
			Thread.ofVirtual().name("translator").start(this::drain);
		}
	}

	private void drain() {
		try {
			while (!this.queue.isEmpty()) {
				List<String> batch = this.queue.stream().limit(BATCH).toList();
				translate(batch);
				batch.forEach(this.queue::remove);
			}
		}
		finally {
			this.working.set(false);
			if (!this.queue.isEmpty()) {
				request(List.of());
			}
		}
	}

	/** Translates one batch with the model and stores what came back. Failures leave the German text in place. */
	void translate(List<String> batch) {
		StringBuilder user = new StringBuilder();
		for (int i = 0; i < batch.size(); i++) {
			user.append(i + 1).append(". ").append(batch.get(i)).append('\n');
		}
		Map<Integer, String> lines;
		try {
			lines = parse(this.llm.complete(SYSTEM, user.toString()).orElse(""));
		}
		catch (RuntimeException ex) {
			log.warn("Translation of {} titles failed: {}", batch.size(), ex.toString());
			return;
		}
		int stored = 0;
		for (int i = 0; i < batch.size(); i++) {
			String source = batch.get(i);
			String text = lines.get(i + 1);
			if (plausible(source, text)) {
				text = tidy(source, text);
				this.jdbc.sql("""
						INSERT INTO translation (source, lang, text) VALUES (?, 'en', ?)
						ON CONFLICT (source, lang) DO UPDATE SET text = excluded.text
						""").params(source, text).update();
				this.english.put(source, text);
				stored++;
			}
		}
		log.info("Translated {} of {} titles", stored, batch.size());
	}

	/** English from the glossary or a fixed title pattern, without the model; null if neither applies. */
	static String fixed(String german) {
		String glossary = GLOSSARY.get(german);
		if (glossary != null) {
			return glossary;
		}
		Matcher talk = TALK.matcher(german);
		if (talk.matches()) {
			return "Talk by " + talk.group(1) + ": " + talk.group(2).replace('„', '"').replace('“', '"');
		}
		// "Labor Grand Challenges of Machine Learning": an English module name with the German word for lab.
		Matcher lab = LAB.matcher(german);
		if (lab.matches() && looksEnglish(lab.group(1))) {
			return lab.group(1) + " Lab";
		}
		return null;
	}

	private static final Pattern ENGLISH_HINT = Pattern.compile("\\b(of|and|for|the|in|to|with)\\b|\\w(ing|ion|ics|ity)\\b",
			Pattern.CASE_INSENSITIVE);

	/** No German letters or words, and some sign of English: "Grand Challenges of Machine Learning", not "Netzwerke". */
	static boolean looksEnglish(String text) {
		return !text.matches(".*[äöüÄÖÜß].*") && words(text).stream().noneMatch(GERMAN_WORDS::contains)
				&& ENGLISH_HINT.matcher(text).find();
	}

	/** Numbered lines of a model reply. */
	static Map<Integer, String> parse(String reply) {
		Map<Integer, String> lines = new LinkedHashMap<>();
		Matcher m = LINE.matcher(reply);
		while (m.find()) {
			lines.putIfAbsent(Integer.parseInt(m.group(1)), m.group(2).replaceAll("^[\"“]|[\"”]$", "").strip());
		}
		return lines;
	}

	/**
	 * Keeps the original when it was already English (the model likes to "improve" those) and
	 * restores a closing quote the model dropped.
	 */
	static String tidy(String source, String text) {
		if (alreadyEnglish(source, text)) {
			return source;
		}
		if (count(source, '"') % 2 == 0 && count(text, '"') % 2 == 1) {
			return text + '"';
		}
		return text;
	}

	/** Most words came back the same (allowing small spelling changes): the title was not German. */
	static boolean alreadyEnglish(String source, String text) {
		if (source.equalsIgnoreCase(text)) {
			return true;
		}
		if (source.matches(".*[äöüÄÖÜß].*")) {
			return false;
		}
		// A quoted part ("Vortrag von X zu \"Graph Neural Networks\"") is a name and stays as it is;
		// what's around it decides.
		String unquoted = source.replaceAll("[\"„“”][^\"„“”]*[\"„“”]", " ");
		if (words(unquoted).stream().anyMatch(GERMAN_WORDS::contains)) {
			return false;
		}
		List<String> before = words(source);
		List<String> after = words(text);
		if (before.isEmpty()) {
			return false;
		}
		long kept = before.stream().filter((w) -> after.stream().anyMatch((a) -> similar(w, a))).count();
		return kept >= Math.ceil(before.size() * 0.75);
	}

	private static List<String> words(String text) {
		return java.util.Arrays.stream(text.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			.filter((w) -> !w.isEmpty())
			.toList();
	}

	private static boolean similar(String a, String b) {
		// "modelling" and "modeling", but not "Projekt" and "Project".
		return a.equals(b) || (a.length() >= 8 && b.length() >= 8 && distance(a, b) <= 1);
	}

	private static int distance(String a, String b) {
		int[] prev = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			int[] cur = new int[b.length() + 1];
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			prev = cur;
		}
		return prev[b.length()];
	}

	private static int count(String text, char c) {
		return (int) text.chars().filter((x) -> x == c).count();
	}

	/** Rejects empty or runaway answers (an explanation instead of a title). */
	static boolean plausible(String source, String text) {
		return text != null && !text.isBlank() && text.length() <= source.length() * 2 + 20;
	}

	private void load() {
		if (this.loaded) {
			return;
		}
		synchronized (this) {
			if (!this.loaded) {
				this.jdbc.sql("SELECT source, text FROM translation WHERE lang = 'en'")
					.query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
					.list()
					.forEach((e) -> this.english.put(e.getKey(), e.getValue()));
				this.loaded = true;
			}
		}
	}

}
