package de.unikassel.assistant.feed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.unikassel.assistant.feed.FeedParser.ParsedItem;
import de.unikassel.assistant.feed.FeedProperties.Kind;
import de.unikassel.assistant.feed.FeedProperties.Source;
import de.unikassel.assistant.feed.FeedStore.StoredItem;
import de.unikassel.assistant.ingest.PageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps the news and events feed current. Unlike the crawler, it only reads the few listing pages
 * in {@code assistant.feed.sources}, so it can run every few hours.
 */
@Service
public class FeedService {

	private static final Logger log = LoggerFactory.getLogger(FeedService.class);

	static final int NEWS_DAYS_BACK = 60;

	static final int EVENT_DAYS_AHEAD = 120;

	private final FeedProperties config;

	private final Map<String, Source> sourcesById;

	private final PageFetcher fetcher;

	private final FeedParser parser;

	private final FeedStore store;

	private final AtomicBoolean running = new AtomicBoolean();

	FeedService(FeedProperties config, PageFetcher fetcher, FeedParser parser, FeedStore store) {
		this.config = config;
		this.sourcesById = config.sources().stream().collect(Collectors.toMap(Source::id, Function.identity()));
		this.fetcher = fetcher;
		this.parser = parser;
		this.store = store;
	}

	@Scheduled(cron = "${assistant.feed.cron}")
	void scheduledRefresh() {
		startInBackground();
	}

	/** Fills an empty feed right away instead of waiting for the first scheduled run. */
	@EventListener(ApplicationReadyEvent.class)
	void refreshIfEmpty() {
		if (this.store.isEmpty()) {
			startInBackground();
		}
	}

	/** Starts a refresh on a background thread. Returns false if one is already running. */
	public boolean startInBackground() {
		if (!this.running.compareAndSet(false, true)) {
			return false;
		}
		Thread.ofVirtual().name("feed").start(() -> {
			try {
				refresh();
			}
			finally {
				this.running.set(false);
			}
		});
		return true;
	}

	private void refresh() {
		for (Source source : this.config.sources()) {
			try {
				var page = this.fetcher.fetch(source.url());
				if (page.isEmpty()) {
					continue;
				}
				List<ParsedItem> items = this.parser.parse(new String(page.get().body(), StandardCharsets.UTF_8),
						page.get().url());
				this.store.save(source, items, Instant.now());
				if (items.isEmpty()) {
					log.warn("Feed source {} returned no items; check its URL and markup", source.id());
				}
				else {
					log.info("Feed source {}: {} items", source.id(), items.size());
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (Exception ex) {
				log.warn("Feed source {} failed: {}", source.id(), ex.toString());
			}
		}
	}

	/** Recent news and upcoming events for a department (null: university-wide only). */
	public Feed feed(String department, LocalDate today) {
		List<Item> news = items(Kind.NEWS, department, today.minusDays(NEWS_DAYS_BACK), today, true, 12);
		List<Item> events = items(Kind.EVENT, department, today, today.plusDays(EVENT_DAYS_AHEAD), false, 12);
		return new Feed(news, events, this.config.covers(department));
	}

	/**
	 * The department's items followed by the university-wide ones, each up to {@code limit}, so
	 * busy university calendars never crowd out a department's own events.
	 */
	private List<Item> items(Kind kind, String department, LocalDate from, LocalDate to, boolean newestFirst, int limit) {
		List<Item> items = new ArrayList<>();
		if (department != null) {
			items.addAll(scope(kind, department, from, to, newestFirst, limit));
		}
		items.addAll(scope(kind, null, from, to, newestFirst, limit));
		return items;
	}

	private List<Item> scope(Kind kind, String department, LocalDate from, LocalDate to, boolean newestFirst, int limit) {
		// Departments often list the same event under news and events with different URLs.
		Set<String> seen = new HashSet<>();
		List<Item> items = new ArrayList<>();
		for (StoredItem item : this.store.find(kind, department, from, to, newestFirst, limit * 2)) {
			if (items.size() < limit && seen.add(item.title() + '|' + item.date())) {
				Source source = this.sourcesById.get(item.sourceId());
				items.add(new Item(item.title(), item.url(), item.date(), item.category(),
						(source != null) ? source.label() : item.sourceId(), item.department(), item.firstSeenAt()));
			}
		}
		return items;
	}

	/** @param departmentCovered whether the department has its own sources, or only sees university-wide items */
	public record Feed(List<Item> news, List<Item> events, boolean departmentCovered) {
	}

	/** @param department null for university-wide items */
	public record Item(String title, String url, LocalDate date, String category, String source, String department,
			Instant firstSeenAt) {
	}

}
