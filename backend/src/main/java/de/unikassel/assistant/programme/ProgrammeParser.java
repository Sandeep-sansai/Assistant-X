package de.unikassel.assistant.programme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unikassel.assistant.programme.Programme.Group;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.Programme.Offered;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * Reads the pages of the university's module database: the examination regulations
 * (showFPO.php) with their study and examination plan, and the module descriptions.
 */
@Component
class ProgrammeParser {

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	/** An area heading in the plan, e.g. "8.1.2: Advanced Courses (18 cp)" or "5: Wahlpflicht … (6 cp)". */
	private static final Pattern GROUP = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?:\\s*(.+?)(?:\\s*\\((\\d+)\\s*cp\\))?$", FLAGS);

	private static final Map<String, Integer> NUMBER_WORDS = Map.of("zwei", 2, "drei", 3, "vier", 4, "fünf", 5,
			"sechs", 6, "sieben", 7, "acht", 8);

	/** The rules a plan depends on, from §3 and §10 of the regulations. */
	record Rules(int totalCredits, int regularSemesters, int thesisCredits, int thesisMinCredits, int thesisWeeks) {
	}

	/** A module as listed in the plan, before its description page is read. */
	record ModuleRef(String code, String title, int credits, String url) {
	}

	record Plan(Rules rules, List<Group> groups, Map<String, ModuleRef> modules) {
	}

	Plan plan(String html, String pageUrl) {
		Document doc = Jsoup.parse(html, pageUrl);
		String text = doc.text();
		Rules rules = new Rules(number(text, "insgesamt (\\d+) Credits", 120),
				wordNumber(text, "Regelstudienzeit[^.]{0,80}?beträgt[^.]{0,60}?(\\p{L}+|\\d+) Semester", 4),
				number(text, "entfallen (\\d+) Credits auf das Masterabschlussmodul", 30),
				number(text, "Masterarbeit wird[^.]{0,120}?mindestens (\\d+) CP", 0),
				number(text, "Bearbeitungszeit der Masterarbeit beträgt (\\d+) Wochen", 0));

		Element table = studyPlanTable(doc);
		List<Group> groups = new ArrayList<>();
		Map<String, ModuleRef> modules = new LinkedHashMap<>();
		if (table == null) {
			return new Plan(rules, groups, modules);
		}
		String code = null;
		String title = null;
		Integer credits = null;
		List<String> members = new ArrayList<>();
		for (Element row : table.select("tr")) {
			List<String> cells = row.select("> td, > th").eachText();
			Element link = row.selectFirst("a[href*=show]");
			if (cells.size() == 1) {
				Matcher m = GROUP.matcher(cells.getFirst().strip());
				if (m.matches()) {
					if (code != null) {
						groups.add(new Group(code, title, credits, List.copyOf(members)));
					}
					code = m.group(1);
					title = m.group(2).strip();
					credits = (m.group(3) != null) ? Integer.valueOf(m.group(3)) : null;
					members.clear();
				}
				continue;
			}
			if (link != null && code != null && cells.size() >= 3) {
				String moduleCode = cells.get(0).strip();
				members.add(moduleCode);
				modules.putIfAbsent(moduleCode, new ModuleRef(moduleCode, cells.get(1).strip(),
						number(cells.get(cells.size() - 1), "(\\d+)", 0), link.absUrl("href")));
			}
		}
		if (code != null) {
			groups.add(new Group(code, title, credits, List.copyOf(members)));
		}
		return new Plan(rules, groups, modules);
	}

	/** The plan table is the one whose headings are numbered areas and whose rows link to modules. */
	private static Element studyPlanTable(Document doc) {
		for (Element table : doc.select("table")) {
			boolean hasAreas = table.select("tr").stream()
				.anyMatch((r) -> r.select("> td, > th").size() == 1 && GROUP.matcher(r.text().strip()).matches());
			if (hasAreas && table.selectFirst("a[href*=show]") != null) {
				return table;
			}
		}
		return null;
	}

	Module module(String html, ModuleRef ref) {
		Document doc = Jsoup.parse(html, ref.url());
		Map<String, String> fields = new LinkedHashMap<>();
		for (Element row : doc.select("tr")) {
			List<String> cells = row.select("> td, > th").eachText();
			if (cells.size() >= 2) {
				fields.putIfAbsent(cells.get(0).strip(), cells.get(1).strip());
			}
		}
		String offeredText = fields.getOrDefault("Häufigkeit des Angebotes", "");
		String exam = fields.getOrDefault("Prüfungsleistungen", "");
		String credits = fields.getOrDefault("Anzahl Credits (ECTS)", "");
		String duration = fields.getOrDefault("Dauer des Moduls", "");
		return new Module(ref.code(), fields.getOrDefault("Modulname", ref.title()),
				number(credits, "(\\d+)", ref.credits()), offered(offeredText), offeredText,
				fields.getOrDefault("Sprache", ""), duration.toLowerCase(Locale.ROOT).startsWith("zwei") ? 2 : 1,
				exam.toLowerCase(Locale.ROOT).contains("mündlich"), exam,
				fields.getOrDefault("Voraussetzungen für die Teilnahme am Modul", ""), ref.url());
	}

	static Offered offered(String text) {
		String t = text.toLowerCase(Locale.ROOT);
		boolean winter = t.contains("winter");
		boolean summer = t.contains("sommer");
		if ((winter && summer) || t.contains("jedes semester") || t.contains("jedem semester") || t.contains("semesterweise")) {
			return Offered.BOTH;
		}
		if (winter) {
			return Offered.WINTER;
		}
		return summer ? Offered.SUMMER : Offered.UNKNOWN;
	}

	private static int number(String text, String regex, int fallback) {
		Matcher m = Pattern.compile(regex, FLAGS).matcher(text);
		return m.find() ? Integer.parseInt(m.group(1)) : fallback;
	}

	private static int wordNumber(String text, String regex, int fallback) {
		Matcher m = Pattern.compile(regex, FLAGS).matcher(text);
		if (!m.find()) {
			return fallback;
		}
		String word = m.group(1).toLowerCase(Locale.ROOT);
		return word.chars().allMatch(Character::isDigit) ? Integer.parseInt(word) : NUMBER_WORDS.getOrDefault(word, fallback);
	}

}
