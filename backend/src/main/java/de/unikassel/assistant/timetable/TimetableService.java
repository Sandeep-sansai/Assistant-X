package de.unikassel.assistant.timetable;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.ProgrammeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly course times per programme and semester, so the study planner can show a week view and
 * spot clashes. Times come from the eCampus course catalogue (imported with the IT service's
 * permission, docs/decisions.md #20); course titles are matched to the programme's modules.
 */
@Service
public class TimetableService {

	private static final Logger log = LoggerFactory.getLogger(TimetableService.class);

	private static final Pattern TERM = Pattern.compile("(WS|SS)\\d{4}");

	/**
	 * Words the catalogue puts around a module's name: "Vorlesung: Deep Learning (Übung)". "Labor",
	 * "Seminar" and "Praktikum" stay: they name different modules ("Labor Deep Learning").
	 */
	private static final Pattern NOISE = Pattern.compile(
			"\\b(vorlesung|vorlesungen|übung|übungen|uebung|ubung|tutorium|tutorien|lecture|exercise|exercises|tutorial|vl|ü|ue|zu|zur|zum|to|for|integrierte)\\b",
			Pattern.UNICODE_CHARACTER_CLASS);

	private final JdbcClient jdbc;

	private final ProgrammeService programmes;

	TimetableService(JdbcClient jdbc, ProgrammeService programmes) {
		this.jdbc = jdbc;
		this.programmes = programmes;
	}

	/**
	 * Replaces the stored times of one programme and semester with an import. Returns how many
	 * courses matched a module of the programme.
	 */
	@Transactional
	public ImportResult replace(String programmeId, String term, List<Course> courses) {
		if (!TERM.matcher(term).matches()) {
			throw new IllegalArgumentException("term must look like WS2026 or SS2027");
		}
		Programme programme = this.programmes.programme(programmeId)
			.orElseThrow(() -> new IllegalArgumentException("unknown programme " + programmeId));
		this.jdbc.sql("DELETE FROM course_slot WHERE programme_id = ? AND term = ?").params(programmeId, term).update();
		int matched = 0;
		int slots = 0;
		for (Course course : courses) {
			List<String> codes = (course.moduleCode() != null && programme.modules().containsKey(course.moduleCode()))
					? List.of(course.moduleCode()) : matchAll(course.title(), programme.modules());
			// The import covers the whole university; only courses of this programme are kept.
			if (codes.isEmpty()) {
				continue;
			}
			matched++;
			for (String code : codes) {
			for (Slot s : course.slots()) {
				if (s.weekday() < 1 || s.weekday() > 7 || !s.end().isAfter(s.start())) {
					continue;
				}
				this.jdbc.sql("""
						INSERT INTO course_slot (programme_id, term, course_title, module_code, kind, weekday, start_time,
						                         end_time, room, source_url)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""")
					.params(programmeId, term, course.title(), code, s.kind(), s.weekday(), s.start(), s.end(), s.room(),
							course.url())
					.update();
				slots++;
			}
			}
		}
		log.info("Timetable {} {}: {} courses ({} matched to modules), {} slots", programmeId, term, courses.size(),
				matched, slots);
		return new ImportResult(courses.size(), matched, slots);
	}

	/** Stored times of a programme for the given semesters, by module where one matched. */
	public List<StoredSlot> slots(String programmeId, List<String> terms) {
		List<String> valid = terms.stream().filter((t) -> TERM.matcher(t).matches()).toList();
		if (valid.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				SELECT term, course_title, module_code, kind, weekday, start_time, end_time, room, source_url
				FROM course_slot WHERE programme_id = :p AND term IN (:terms)
				ORDER BY term, weekday, start_time
				""")
			.param("p", programmeId)
			.param("terms", valid)
			.query((rs, n) -> new StoredSlot(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					rs.getInt(5), rs.getObject(6, LocalTime.class), rs.getObject(7, LocalTime.class), rs.getString(8),
					rs.getString(9)))
			.list();
	}

	/** Semesters with imported times for a programme, newest first. */
	public List<String> terms(String programmeId) {
		return this.jdbc.sql("SELECT DISTINCT term FROM course_slot WHERE programme_id = ?")
			.param(programmeId)
			.query(String.class)
			.list()
			.stream()
			.sorted(Comparator.comparing((String t) -> Integer.parseInt(t.substring(2)) * 2 + (t.startsWith("WS") ? 1 : 0))
				.reversed())
			.toList();
	}

	/**
	 * The module a catalogue course belongs to: its code appears in the title, or the titles are the
	 * same once course-type words ("Vorlesung", "Übung") and punctuation are removed.
	 */
	static String match(String courseTitle, Map<String, Module> modules) {
		List<String> all = matchAll(courseTitle, modules);
		return all.isEmpty() ? null : all.getFirst();
	}

	/**
	 * Every module a course counts for. The regulations list some modules under several areas with
	 * different codes (e.g. "Deep Learning" as PI-26 and TeI-08); a course then belongs to all of them.
	 */
	static List<String> matchAll(String courseTitle, Map<String, Module> modules) {
		String title = courseTitle.toLowerCase(Locale.ROOT);
		for (Module m : modules.values()) {
			if (Pattern.compile("(?<![\\p{L}\\p{N}-])" + Pattern.quote(m.code().toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}-])")
				.matcher(title)
				.find()) {
				return List.of(m.code());
			}
		}
		String key = normalize(courseTitle);
		List<Module> candidates = new ArrayList<>();
		for (Module m : modules.values()) {
			String name = normalize(m.title());
			if (!name.isEmpty() && (key.equals(name) || (key.startsWith(name + " ") && name.length() >= 8))) {
				candidates.add(m);
			}
		}
		// The longest name wins: "Pattern Recognition and Machine Learning II" over "... I"; modules with
		// that same name all count.
		int longest = candidates.stream().mapToInt((m) -> normalize(m.title()).length()).max().orElse(0);
		return candidates.stream().filter((m) -> normalize(m.title()).length() == longest).map(Module::code).sorted().toList();
	}

	/**
	 * "Vorlesung/Übung · 2. PG" → "Lecture/exercise · group 2" when English is wanted. The catalogue
	 * only has German labels.
	 */
	public static String kindLabel(String kind, boolean english) {
		if (kind == null || kind.isBlank() || !english) {
			return (kind == null) ? "" : kind;
		}
		String k = kind;
		for (String[] r : KIND_EN) {
			k = k.replaceAll(r[0], r[1]);
		}
		return k;
	}

	/** "Hörsaal 0425 (WA-altes Gebäude (WA 73) (EG))" → "Lecture hall 0425 (WA old building (WA 73) (ground floor))". */
	public static String roomLabel(String room, boolean english) {
		if (room == null || room.isBlank() || !english) {
			return (room == null) ? "" : room;
		}
		String r = room;
		for (String[] x : ROOM_EN) {
			r = r.replaceAll(x[0], x[1]);
		}
		return r;
	}

	private static final String[][] KIND_EN = { { "Vorlesung/Übung", "Lecture/exercise" }, { "Vorlesung", "Lecture" },
			{ "Übungen", "Exercises" }, { "Übung", "Exercise" }, { "Tutorium", "Tutorial" }, { "Praktikum", "Practical" },
			{ "Labor", "Lab" }, { "Projekt", "Project" }, { "Kolloquium", "Colloquium" }, { "Blockveranstaltung", "Block course" },
			{ "(\\d+)\\. PG", "group $1" } };

	private static final String[][] ROOM_EN = { { "Hörsaal", "Lecture hall" }, { "Seminarraum", "Seminar room" },
			{ "Raum", "Room" }, { "Labor", "Lab" }, { "-altes Gebäude", " old building" }, { "-neues Gebäude", " new building" },
			{ "Gebäude", "building" }, { "\\(EG\\)", "(ground floor)" }, { "\\((\\d)\\. ?OG\\)", "(floor $1)" },
			{ "\\((\\d)\\. ?UG\\)", "(basement $1)" }, { "\\bUG\\b", "basement" }, { "\\bOG\\b", "floor" } };

	public static String normalize(String text) {
		String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
		plain = plain.replaceAll("\\([^)]*\\)", " ");
		plain = NOISE.matcher(plain).replaceAll(" ");
		return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").strip();
	}

	public record Course(String title, String moduleCode, String url, List<Slot> slots) {

		public Course {
			slots = (slots == null) ? List.of() : slots;
		}

	}

	/** @param weekday ISO day, 1 = Monday */
	public record Slot(String kind, int weekday, LocalTime start, LocalTime end, String room) {
	}

	public record StoredSlot(String term, String courseTitle, String moduleCode, String kind, int weekday,
			LocalTime start, LocalTime end, String room, String sourceUrl) {
	}

	public record ImportResult(int courses, int matched, int slots) {
	}

}
