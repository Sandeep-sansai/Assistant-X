package de.unikassel.assistant.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import org.springframework.stereotype.Component;

/** Reads page URLs from a sitemap, following sitemap indexes. */
@Component
class SitemapReader {

	private final PageFetcher fetcher;

	SitemapReader(PageFetcher fetcher) {
		this.fetcher = fetcher;
	}

	List<String> read(String sitemapUrl) throws IOException, InterruptedException {
		List<String> urls = new ArrayList<>();
		read(sitemapUrl, urls, 0);
		return urls;
	}

	private void read(String sitemapUrl, List<String> urls, int depth) throws IOException, InterruptedException {
		var page = this.fetcher.fetch(sitemapUrl);
		if (page.isEmpty()) {
			return;
		}
		Document xml = Jsoup.parse(new String(page.get().body(), StandardCharsets.UTF_8), "", Parser.xmlParser());
		for (Element loc : xml.select("url > loc")) {
			urls.add(loc.text().trim());
		}
		if (depth < 2) {
			for (Element loc : xml.select("sitemap > loc")) {
				read(loc.text().trim(), urls, depth + 1);
			}
		}
	}

}
