package de.unikassel.assistant.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unikassel.assistant.agent.AgentReply.Action;
import de.unikassel.assistant.agent.AgentReply.Card;
import de.unikassel.assistant.i18n.Translator;
import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.ProgrammeProperties.Source;
import de.unikassel.assistant.programme.ProgrammeService;
import de.unikassel.assistant.timetable.TimetableService;
import de.unikassel.assistant.timetable.TimetableService.StoredSlot;

import org.springframework.stereotype.Component;

/**
 * Class times from the imported eCampus course catalogue: "When is Deep Learning?", "What classes
 * do I have today?", "Lectures on Monday". Uses the modules the student planned for this semester
 * when there are any.
 */
@Component
class TimetableAgent implements Agent {

	static final String ID = "timetable";

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	/** Words that ask about class times rather than about rules or deadlines. */
	private static final Pattern TIME_WORDS = Pattern.compile(
			"\\b(class|classes|lecture|lectures|lessons?|courses?|timetable|schedule|tutorial|exercise|seminar"
					+ "|vorlesung(?:en)?|übung(?:en)?|kurse?|stundenplan|veranstaltung(?:en)?|unterricht)\\b"
					+ "|when (?:is|are|does|do)|what time|which day|wann (?:ist|sind|findet|habe)|um wie viel uhr|an welchem tag",
			FLAGS);

	private static final Pattern DAY_WORDS = Pattern.compile(
			"\\b(today|tomorrow|heute|morgen|monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch"
					+ "|donnerstag|freitag|samstag|sonntag|mon|tue|wed|thu|fri)\\b",
			FLAGS);

	private static final Map<String, DayOfWeek> DAYS = Map.ofEntries(Map.entry("monday", DayOfWeek.MONDAY),
			Map.entry("montag", DayOfWeek.MONDAY), Map.entry("mon", DayOfWeek.MONDAY), Map.entry("tuesday", DayOfWeek.TUESDAY),
			Map.entry("dienstag", DayOfWeek.TUESDAY), Map.entry("tue", DayOfWeek.TUESDAY),
			Map.entry("wednesday", DayOfWeek.WEDNESDAY), Map.entry("mittwoch", DayOfWeek.WEDNESDAY),
			Map.entry("wed", DayOfWeek.WEDNESDAY), Map.entry("thursday", DayOfWeek.THURSDAY),
			Map.entry("donnerstag", DayOfWeek.THURSDAY), Map.entry("thu", DayOfWeek.THURSDAY),
			Map.entry("friday", DayOfWeek.FRIDAY), Map.entry("freitag", DayOfWeek.FRIDAY), Map.entry("fri", DayOfWeek.FRIDAY),
			Map.entry("saturday", DayOfWeek.SATURDAY), Map.entry("samstag", DayOfWeek.SATURDAY),
			Map.entry("sunday", DayOfWeek.SUNDAY), Map.entry("sonntag", DayOfWeek.SUNDAY));

	private final ProgrammeService programmes;

	private final TimetableService timetable;

	private final Translator translator;

	TimetableAgent(ProgrammeService programmes, TimetableService timetable, Translator translator) {
		this.programmes = programmes;
		this.timetable = timetable;
		this.translator = translator;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public double relevance(AgentRequest r) {
		if (!TIME_WORDS.matcher(r.message()).find()) {
			return 0;
		}
		if (DAY_WORDS.matcher(r.message()).find() && Pattern.compile("\\b(class|classes|lecture|lectures|lessons?|courses?|vorlesung(?:en)?|kurse?|veranstaltung(?:en)?|stundenplan|timetable|schedule)\\b", FLAGS).matcher(r.message()).find()) {
			return 0.85;
		}
		return programme(r).map((p) -> mentioned(r.message(), p).isEmpty() ? 0.0 : 0.85).orElse(0.0);
	}

	@Override
	public AgentReply handle(AgentRequest r) {
		Optional<Programme> programme = programme(r);
		if (programme.isEmpty()) {
			return AgentReply.of(ID, r.say("I only know class times for the programmes the study planner supports. Pick yours in your profile and ask again.",
					"Ich kenne Vorlesungszeiten nur für die Studiengänge, die der Studienplaner unterstützt. Wähl deinen im Profil aus und frag noch einmal."));
		}
		Programme p = programme.get();
		String term = PlannerAgent.currentTerm(r.now().toLocalDate());
		List<StoredSlot> slots = this.timetable.slots(p.id(), List.of(term));
		if (slots.isEmpty()) {
			return AgentReply.of(ID, r.say("I don't have class times for %s yet. They're imported from eCampus every night once they're published.",
					"Für das %s habe ich noch keine Vorlesungszeiten. Sie werden jede Nacht aus dem eCampus übernommen, sobald sie veröffentlicht sind.")
				.formatted(PlannerAgent.termLabel(term, r)));
		}
		List<Module> asked = mentioned(r.message(), p);
		if (!asked.isEmpty()) {
			return moduleTimes(r, p, term, slots, asked);
		}
		return dayTimes(r, p, term, slots, day(r.message(), r.now().toLocalDate()));
	}

	/** "When is Deep Learning?": every weekly slot of the modules named in the question. */
	private AgentReply moduleTimes(AgentRequest r, Programme p, String term, List<StoredSlot> slots, List<Module> asked) {
		List<Card> cards = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		for (Module m : asked) {
			List<StoredSlot> own = slots.stream().filter((s) -> m.code().equals(s.moduleCode())).toList();
			if (own.isEmpty()) {
				lines.add(r.say("**%s**: no weekly times on eCampus this semester (it may be a block course or not offered now).",
						"**%s**: keine wöchentlichen Termine im eCampus in diesem Semester (vielleicht eine Blockveranstaltung oder gerade nicht angeboten).")
					.formatted(name(m, r)));
				continue;
			}
			lines.add("**%s**: %s".formatted(name(m, r), String.join(", ", own.stream().map((s) -> when(s, r)).distinct().toList())));
			own.stream().limit(6).forEach((s) -> cards.add(card(s, name(m, r), r)));
		}
		String text = r.say("Here's when it meets in %s, from the eCampus course catalogue:\n", "So liegt es im %s laut Vorlesungsverzeichnis im eCampus:\n")
			.formatted(PlannerAgent.termLabel(term, r)) + String.join("\n", lines);
		return AgentReply.of(ID, text, cards, List.of(openTimetable(r)));
	}

	/** "What classes do I have today?": the student's planned modules on that day, or the programme's. */
	private AgentReply dayTimes(AgentRequest r, Programme p, String term, List<StoredSlot> slots, LocalDate date) {
		DayOfWeek day = date.getDayOfWeek();
		String dayName = day.getDisplayName(TextStyle.FULL, r.german() ? Locale.GERMAN : Locale.ENGLISH);
		boolean planned = !r.plannedModules().isEmpty();
		List<StoredSlot> onDay = slots.stream()
			.filter((s) -> s.weekday() == day.getValue())
			.filter((s) -> !planned || r.plannedModules().contains(s.moduleCode()))
			.sorted((a, b) -> a.start().compareTo(b.start()))
			.toList();
		if (day.getValue() >= 6) {
			return AgentReply.of(ID, r.say("%s is a weekend day: no regular classes.", "%s ist Wochenende: keine regulären Veranstaltungen.").formatted(dayName));
		}
		if (onDay.isEmpty()) {
			return AgentReply.of(ID, planned
					? r.say("None of the modules in your plan for this semester meet on %s.", "Keins der Module in deinem Plan für dieses Semester findet am %s statt.").formatted(dayName)
					: r.say("I don't see any %s classes on %s.", "Am %s finde ich keine Veranstaltungen im %s.").formatted(p.title(), dayName),
					List.of(), List.of(openTimetable(r)));
		}
		List<Card> cards = new ArrayList<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (StoredSlot s : onDay) {
			Module m = p.modules().get(s.moduleCode());
			String n = (m != null) ? name(m, r) : s.courseTitle();
			names.putIfAbsent(s.moduleCode() + s.start(), n);
			if (cards.size() < 12) {
				cards.add(card(s, n, r));
			}
		}
		String text = planned
				? r.say("Your planned classes on %s:", "Deine geplanten Veranstaltungen am %s:").formatted(dayName)
				: r.say("%s classes on %s (add your modules in the study planner to see only yours):",
						"Veranstaltungen im %s am %s (plane deine Module im Studienplaner, dann siehst du nur deine):")
					.formatted(p.title(), dayName);
		return AgentReply.of(ID, text, cards, List.of(openTimetable(r)));
	}

	private static Card card(StoredSlot s, String name, AgentRequest r) {
		boolean en = !r.german();
		String day = DayOfWeek.of(s.weekday()).getDisplayName(TextStyle.SHORT, r.german() ? Locale.GERMAN : Locale.ENGLISH);
		String title = "%s %s–%s · %s".formatted(day, s.start(), s.end(), name);
		String body = String.join(" · ", List.of(TimetableService.kindLabel(s.kind(), en), TimetableService.roomLabel(s.room(), en))
			.stream().filter((x) -> !x.isBlank()).toList());
		return new Card(title, body, null, null);
	}

	private static String when(StoredSlot s, AgentRequest r) {
		String day = DayOfWeek.of(s.weekday()).getDisplayName(TextStyle.SHORT, r.german() ? Locale.GERMAN : Locale.ENGLISH);
		String kind = TimetableService.kindLabel(s.kind(), !r.german());
		return "%s %s–%s%s".formatted(day, s.start(), s.end(), kind.isBlank() ? "" : " (" + kind + ")");
	}

	private static Action openTimetable(AgentRequest r) {
		return new Action("OPEN_PLANNER", r.say("Open my timetable", "Meinen Stundenplan öffnen"), null, null, null, Map.of("view", "week"));
	}

	private String name(Module m, AgentRequest r) {
		return r.german() ? m.title() : this.translator.english(m.title()).orElse(m.title());
	}

	/** Modules whose name (German or English) appears in the message; the longest names win. */
	List<Module> mentioned(String message, Programme p) {
		String text = " " + TimetableService.normalize(message) + " ";
		List<Module> found = new ArrayList<>();
		for (Module m : p.modules().values()) {
			if (m.credits() >= 30) {
				continue;   // the thesis has no class times
			}
			for (String title : List.of(m.title(), this.translator.english(m.title()).orElse(m.title()))) {
				String n = TimetableService.normalize(title);
				if (n.length() >= 4 && text.contains(" " + n + " ") && found.stream().noneMatch((f) -> f.title().equals(m.title()))) {
					found.add(m);
				}
			}
		}
		// "Pattern Recognition and Machine Learning II" also contains "... I"; keep the longer one.
		return found.stream().filter((m) -> found.stream().noneMatch((o) -> o != m && o.title().startsWith(m.title()) && o.title().length() > m.title().length())).limit(4).toList();
	}

	/** today / tomorrow / a weekday name / "19 oct"; defaults to today. */
	static LocalDate day(String message, LocalDate today) {
		String m = message.toLowerCase(Locale.ROOT);
		if (Pattern.compile("\\b(tomorrow|morgen)\\b", FLAGS).matcher(m).find() && !m.contains("morgens")) {
			return today.plusDays(1);
		}
		Matcher d = DAY_WORDS.matcher(m);
		while (d.find()) {
			DayOfWeek day = DAYS.get(d.group(1).toLowerCase(Locale.ROOT));
			if (day != null) {
				LocalDate date = today;
				while (date.getDayOfWeek() != day) {
					date = date.plusDays(1);
				}
				return date;
			}
		}
		return today;
	}

	private Optional<Programme> programme(AgentRequest r) {
		Optional<Source> source = this.programmes.sourceFor(r.department(), r.programme());
		if (source.isEmpty() && this.programmes.sources().size() == 1 && "fb16".equals(r.department())) {
			source = Optional.of(this.programmes.sources().getFirst());
		}
		return source.flatMap((s) -> this.programmes.programme(s.id()));
	}

}
