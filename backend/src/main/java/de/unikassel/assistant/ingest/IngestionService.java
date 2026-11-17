package de.unikassel.assistant.ingest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import de.unikassel.assistant.config.AssistantProperties;
import de.unikassel.assistant.ingest.ContentExtractor.ExtractedPage;
import de.unikassel.assistant.ingest.PageFetcher.FetchedPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Crawls the configured university pages and keeps the document and chunk tables in sync with
 * them. Unchanged pages are detected by a hash of their extracted text and are not re-chunked.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final AssistantProperties.Crawler config;

	private final List<Pattern> followPatterns;

	private final SitemapReader sitemaps;

	private final PageFetcher fetcher;

	private final ContentExtractor extractor;

	private final Chunker chunker;

	private final DocumentStore store;

	private final AtomicBoolean running = new AtomicBoolean();

	IngestionService(AssistantProperties props, SitemapReader sitemaps, PageFetcher fetcher,
			ContentExtractor extractor, Chunker chunker, DocumentStore store) {
		this.config = props.crawler();
		this.followPatterns = this.config.followPatterns().stream().map(Pattern::compile).toList();
		this.sitemaps = sitemaps;
		this.fetcher = fetcher;
		this.extractor = extractor;
		this.chunker = chunker;
		this.store = store;
	}

	@Scheduled(cron = "${assistant.crawler.cron}")
	void scheduledRun() {
		startInBackground();
	}

	/** Starts a crawl on a background thread. Returns false if one is already running. */
	public boolean startInBackground() {
		if (!this.running.compareAndSet(false, true)) {
			return false;
		}
		Thread.ofVirtual().name("ingestion").start(() -> {
			try {
				crawl();
			}
			catch (Exception ex) {
				log.error("Ingestion run failed", ex);
			}
			finally {
				this.running.set(false);
			}
		});
		return true;
	}

	public boolean isRunning() {
		return this.running.get();
	}

	private void crawl() throws InterruptedException {
		Instant start = Instant.now();
		long runId = this.store.startRun(start);
		Deque<Target> frontier = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		int fetched = 0;
		int changed = 0;
		int errors = 0;
		try {
			for (String url : startUrls()) {
				if (seen.add(url)) {
					frontier.add(new Target(url, 0));
				}
			}
			log.info("Ingestion run {} starting with {} URLs", runId, frontier.size());

			while (!frontier.isEmpty() && fetched < this.config.maxPages()) {
				Target target = frontier.poll();
				try {
					var page = this.fetcher.fetch(target.url());
					if (page.isEmpty()) {
						continue;
					}
					fetched++;
					seen.add(page.get().url());
					ExtractedPage extracted = extract(page.get());
					if (extracted == null) {
						continue;
					}
					if (store(page.get().url(), extracted)) {
						changed++;
					}
					if (target.depth() < this.config.maxDepth()) {
						for (String next : nextUrls(extracted)) {
							if (seen.add(next)) {
								// Regulations and module descriptions first, so the page limit never cuts them off.
								if (isPriority(next, extracted)) {
									frontier.addFirst(new Target(next, target.depth() + 1));
								}
								else {
									frontier.add(new Target(next, target.depth() + 1));
								}
							}
						}
					}
				}
				catch (InterruptedException ex) {
					throw ex;
				}
				catch (Exception ex) {
					errors++;
					log.warn("Failed to ingest {}: {}", target.url(), ex.toString());
				}
			}

			// Only retire documents after a crawl that reached everything and mostly succeeded.
			boolean complete = frontier.isEmpty() && errors <= fetched / 10;
			if (complete) {
				int gone = this.store.markGoneIfNotSeenSince(start);
				log.info("Marked {} documents as gone", gone);
			}
			else {
				log.warn("Crawl incomplete ({} left in queue, {} errors); not retiring documents", frontier.size(),
						errors);
			}
			this.store.finishRun(runId, "finished", fetched, changed, errors, Instant.now());
			log.info("Ingestion run {} finished: {} fetched, {} changed, {} errors", runId, fetched, changed, errors);
		}
		catch (Exception ex) {
			this.store.finishRun(runId, "failed", fetched, changed, errors, Instant.now());
			throw ex;
		}
	}

	private List<String> startUrls() throws InterruptedException {
		List<String> urls = new ArrayList<>(this.config.seeds());
		for (String sitemap : this.config.sitemaps()) {
			try {
				this.sitemaps.read(sitemap)
					.stream()
					.filter((url) -> this.config.includePrefixes().stream().anyMatch(url::startsWith))
					.forEach(urls::add);
			}
			catch (IOException ex) {
				log.warn("Could not read sitemap {}: {}", sitemap, ex.toString());
			}
		}
		return urls;
	}

	private ExtractedPage extract(FetchedPage page) throws IOException {
		if (page.isPdf()) {
			return this.extractor.pdf(page.body(), page.url());
		}
		if (page.isHtml()) {
			return this.extractor.html(new String(page.body(), charset(page.contentType())), page.url());
		}
		return null;
	}

	/** Returns true if the document was new or its text changed. */
	private boolean store(String url, ExtractedPage page) {
		if (page.sections().isEmpty()) {
			return false;
		}
		String hash = sha256(page.fullText());
		Instant now = Instant.now();
		if (this.store.contentHash(url).filter(hash::equals).isPresent()) {
			this.store.touch(url, now);
			return false;
		}
		this.store.save(url, page.title(), page.contentType(), hash, this.chunker.chunk(page.sections()), now);
		return true;
	}

	private List<String> nextUrls(ExtractedPage page) {
		List<String> next = new ArrayList<>(page.embeds());
		for (String link : page.links()) {
			if (this.followPatterns.stream().anyMatch((p) -> p.matcher(link).matches())) {
				next.add(link);
			}
		}
		return next;
	}

	/** Embedded pages (e.g. the module database inside a programme page) and module database links. */
	private static boolean isPriority(String url, ExtractedPage page) {
		return page.embeds().contains(url) || url.startsWith("https://moduldatenbank.uni-kassel.de/");
	}

	private static Charset charset(String contentType) {
		var m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
		try {
			return m.find() ? Charset.forName(m.group(1)) : StandardCharsets.UTF_8;
		}
		catch (IllegalArgumentException ex) {
			return StandardCharsets.UTF_8;
		}
	}

	private static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record Target(String url, int depth) {
	}

}
