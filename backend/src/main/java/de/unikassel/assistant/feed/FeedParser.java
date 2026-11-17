package de.unikassel.assistant.feed;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * Reads items from the news and event listing pages of the university site. They are all
 * rendered by the same CMS extension: one {@code .news-list-item} per entry, with the date in a
 * {@code datePublished} meta tag (the event date, for events) and the title in a headline link.
 */
@Component
class FeedParser {

	/** Detail page URLs also carry the date, e.g. /termin/2026/10/15/…; used when the meta tag is missing. */
	private static final Pattern DATE_IN_URL = Pattern.compile("/(\\d{4})/(\\d{1,2})/(\\d{1,2})/");

	List<ParsedItem> parse(String html, String pageUrl) {
		Document doc = Jsoup.parse(html, pageUrl);
		Element root = doc.selectFirst("main#inhalt");
		if (root == null) {
			root = doc.selectFirst("main");
		}
		if (root == null) {
			root = doc.body();
		}
		Map<String, ParsedItem> items = new LinkedHashMap<>();
		for (Element entry : root.select(".news-list-item")) {
			Element link = entry.selectFirst("a.headline-link[href]");
			if (link == null) {
				link = entry.selectFirst("a[href]");
			}
			if (link == null) {
				continue;
			}
			String url = link.absUrl("href");
			String title = (link.hasAttr("title") && !link.attr("title").isBlank()) ? link.attr("title") : link.text();
			LocalDate date = date(entry, url);
			if (url.isEmpty() || title.isBlank() || date == null) {
				continue;
			}
			Element category = entry.selectFirst(".news-cat");
			String cat = (category != null && !category.text().isBlank()) ? category.text().strip() : null;
			items.putIfAbsent(url, new ParsedItem(title.strip(), url, date, cat));
		}
		return List.copyOf(items.values());
	}

	private static LocalDate date(Element entry, String url) {
		Element meta = entry.selectFirst("meta[itemprop=datePublished]");
		if (meta != null && meta.attr("content").length() >= 10) {
			try {
				return LocalDate.parse(meta.attr("content").substring(0, 10));
			}
			catch (DateTimeException ex) {
				// fall back to the URL
			}
		}
		Matcher m = DATE_IN_URL.matcher(url);
		if (m.find()) {
			try {
				return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
						Integer.parseInt(m.group(3)));
			}
			catch (DateTimeException ex) {
				return null;
			}
		}
		return null;
	}

	/** @param category the listing's own category label, e.g. "ECE", or null */
	record ParsedItem(String title, String url, LocalDate date, String category) {
	}

}
