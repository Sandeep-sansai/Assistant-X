package de.unikassel.assistant.ingest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * Turns HTML pages and PDFs into titled sections of plain text, and collects the links and
 * embedded frames worth following.
 */
@Component
class ContentExtractor {

	/** Elements whose text is taken as one unit. */
	private static final String UNITS = "h1,h2,h3,h4,h5,h6,p,li,dt,dd,tr,pre,blockquote";

	/** A unit inside one of these is already covered by its ancestor. */
	private static final Set<String> CONTAINERS = Set.of("p", "li", "dt", "dd", "tr", "pre", "blockquote");

	/** Page chrome inside the content area. */
	private static final String NOISE = "script,style,noscript,nav,header,footer,form,button,.share,[class*=share]";

	/** The uni site embeds the module database with a script call instead of an iframe tag. */
	private static final Pattern SCRIPT_EMBED = Pattern.compile("embed\\('(https?://[^']+)'");

	/** Paragraph headings in examination regulations, e.g. "§ 7 Prüfungsleistungen". */
	private static final Pattern PARAGRAPH_HEADING = Pattern.compile("^§\\s*\\d+[a-z]?\\s+\\S.{0,100}$");

	ExtractedPage html(String html, String url) {
		Document doc = Jsoup.parse(html, url);
		Element root = doc.selectFirst("main#inhalt");
		if (root == null) {
			root = doc.selectFirst("main");
		}
		if (root == null) {
			root = doc.body();
		}

		Set<String> embeds = new LinkedHashSet<>();
		root.select("iframe[src]").forEach((frame) -> embeds.add(frame.absUrl("src")));
		Matcher m = SCRIPT_EMBED.matcher(root.html());
		while (m.find()) {
			embeds.add(m.group(1));
		}
		Set<String> links = new LinkedHashSet<>();
		root.select("a[href]").forEach((a) -> links.add(stripFragment(a.absUrl("href"))));

		root.select(NOISE).remove();
		List<Section> sections = sections(root);
		// <title> is often generic ("Prüfungsordnung") or suffixed ("Rückmeldung - UNI"); the h1 is more specific.
		Element h1 = root.selectFirst("h1");
		String title = (h1 != null && !h1.text().isBlank()) ? h1.text().strip()
				: doc.title().replaceFirst("\\s+[-|]\\s+(UNI|Universität Kassel)$", "").strip();
		return new ExtractedPage(title, "html", sections, List.copyOf(embeds), List.copyOf(links));
	}

	ExtractedPage pdf(byte[] body, String url) throws IOException {
		try (PDDocument pdf = Loader.loadPDF(body)) {
			String title = pdf.getDocumentInformation().getTitle();
			if (title == null || title.isBlank()) {
				title = url.substring(url.lastIndexOf('/') + 1);
			}
			Section current = new Section("", new ArrayList<>());
			List<Section> sections = new ArrayList<>(List.of(current));
			// Paragraphs are separated by blank lines; single line breaks are layout.
			for (String para : new PDFTextStripper().getText(pdf).split("\\n\\s*\\n")) {
				String text = para.replaceAll("\\s+", " ").strip();
				if (text.isEmpty()) {
					continue;
				}
				if (PARAGRAPH_HEADING.matcher(text).matches()) {
					current = new Section(text, new ArrayList<>());
					sections.add(current);
				}
				else {
					current.paragraphs().add(text);
				}
			}
			return new ExtractedPage(title.strip(), "pdf", nonEmpty(sections), List.of(), List.of());
		}
	}

	private List<Section> sections(Element root) {
		String[] headingPath = new String[6];
		Section current = new Section("", new ArrayList<>());
		List<Section> sections = new ArrayList<>(List.of(current));
		for (Element el : root.select(UNITS)) {
			if (hasContainerAncestor(el, root)) {
				continue;
			}
			String text = el.tagName().equals("tr") ? rowText(el) : el.text().strip();
			if (text.isEmpty()) {
				continue;
			}
			int level = headingLevel(el, text);
			if (level > 0) {
				headingPath[level - 1] = text;
				for (int i = level; i < headingPath.length; i++) {
					headingPath[i] = null;
				}
				current = new Section(joinPath(headingPath), new ArrayList<>());
				sections.add(current);
			}
			else {
				current.paragraphs().add(text);
			}
		}
		return nonEmpty(sections);
	}

	private static int headingLevel(Element el, String text) {
		String tag = el.tagName();
		if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
			return tag.charAt(1) - '0';
		}
		// Regulation paragraphs often come as bold <p> elements rather than real headings.
		return PARAGRAPH_HEADING.matcher(text).matches() ? 4 : 0;
	}

	private static boolean hasContainerAncestor(Element el, Element root) {
		for (Element parent = el.parent(); parent != null && parent != root; parent = parent.parent()) {
			if (CONTAINERS.contains(parent.tagName())) {
				return true;
			}
		}
		return false;
	}

	private static String rowText(Element row) {
		List<String> cells = new ArrayList<>();
		row.select("> th, > td").forEach((cell) -> {
			String text = cell.text().strip();
			if (!text.isEmpty()) {
				cells.add(text);
			}
		});
		return String.join(" | ", cells);
	}

	private static String joinPath(String[] path) {
		List<String> parts = new ArrayList<>();
		for (String part : path) {
			if (part != null) {
				parts.add(part);
			}
		}
		return String.join(" > ", parts);
	}

	private static List<Section> nonEmpty(List<Section> sections) {
		return sections.stream().filter((s) -> !s.paragraphs().isEmpty()).toList();
	}

	static String stripFragment(String url) {
		int hash = url.indexOf('#');
		return hash < 0 ? url : url.substring(0, hash);
	}

	/** A run of paragraphs under one heading path, e.g. "Rückmeldung > Fristen". */
	record Section(String heading, List<String> paragraphs) {
	}

	record ExtractedPage(String title, String contentType, List<Section> sections, List<String> embeds,
			List<String> links) {

		String fullText() {
			StringBuilder sb = new StringBuilder(this.title).append('\n');
			for (Section s : this.sections) {
				sb.append(s.heading()).append('\n');
				s.paragraphs().forEach((p) -> sb.append(p).append('\n'));
			}
			return sb.toString();
		}

	}

}
