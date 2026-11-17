package de.unikassel.assistant.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unikassel.assistant.agent.AgentReply.Action;
import de.unikassel.assistant.agent.AgentReply.Card;
import de.unikassel.assistant.i18n.Translator;
import de.unikassel.assistant.planner.PlanRequest;
import de.unikassel.assistant.planner.StudyPlan;
import de.unikassel.assistant.planner.StudyPlanner;
import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.ProgrammeProperties.Source;
import de.unikassel.assistant.programme.ProgrammeService;

import org.springframework.stereotype.Component;

/** Plans the student's degree: "plan my master in 5 semesters with focus CIDA". */
@Component
class PlannerAgent implements Agent {

	static final String ID = "planner";

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern ASKS = Pattern.compile(
			"\\bplan\\b.{0,30}\\b(?:studies|master|degree|semesters?|modules?)|study plan|studienplan|studienverlauf|"
					+ "\\bplane?\\b.{0,30}\\b(?:studium|master|semester|module)|finish (?:my )?(?:master|degree)|"
					+ "(?:master|studium)\\b.{0,30}\\babschließen|which modules should|welche module sollte",
			FLAGS);

	private static final Pattern SEMESTERS = Pattern.compile(
			"\\b(\\d{1,2}|two|three|four|five|six|seven|eight|zwei|drei|vier|fünf|sechs|sieben|acht)\\s+semest", FLAGS);

	private static final Map<String, Integer> WORDS = Map.of("two", 2, "three", 3, "four", 4, "five", 5, "six", 6,
			"seven", 7, "eight", 8, "zwei", 2, "drei", 3, "vier", 4);

	/** Words that name a focus option in the regulations' titles, in English or German. */
	private static final Map<String, List<String>> FOCUS_WORDS = Map.of(
			"cida", List.of("cida", "computational intelligence", "data analytics", "machine learning", "data science"),
			"einf", List.of("einf", "energy", "energie"),
			"idg", List.of("idg", "digital society", "digitale gesellschaft"),
			"(sw)", List.of("software"),
			"(uw)", List.of("environment", "umwelt"),
			"freier", List.of("free focus", "freier schwerpunkt", "own focus", "eigener schwerpunkt"));

	private final ProgrammeService programmes;

	private final Translator translator;

	PlannerAgent(ProgrammeService programmes, Translator translator) {
		this.programmes = programmes;
		this.translator = translator;
	}

	/** A module or area name from the regulations in the student's language (the regulations are German). */
	private String name(String german, AgentRequest r) {
		return r.say(this.translator.english(german).orElse(german), german);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public double relevance(AgentRequest r) {
		return ASKS.matcher(r.message()).find() ? 0.9 : 0;
	}

	@Override
	public AgentReply handle(AgentRequest r) {
		Optional<Source> source = this.programmes.sourceFor(r.department(), r.programme());
		Optional<Programme> programme = source.flatMap((s) -> this.programmes.programme(s.id()));
		if (programme.isEmpty()) {
			String known = this.programmes.sources().stream().map(Source::title).reduce((a, b) -> a + ", " + b).orElse("–");
			return AgentReply.of(ID, r.say(
					"I can plan these programmes so far: %s. Pick yours as your programme in your profile and ask me again.".formatted(known),
					"Bisher kann ich diese Studiengänge planen: %s. Wähle deinen in deinem Profil aus und frag mich noch einmal.".formatted(known)));
		}
		Programme p = programme.get();
		String focus = focus(r.message(), p).orElse(r.planFocus());
		if (focus == null || p.group(focus) == null) {
			List<Action> choices = p.focusOptions()
				.stream()
				.map((f) -> Action.ask(r.say("Plan my master with the focus ", "Plane meinen Master mit dem Schwerpunkt ")
						+ shortTitle(name(f.title(), r))))
				.toList();
			return AgentReply.of(ID, r.say("Which focus would you like? The regulations offer these:",
					"Welchen Schwerpunkt möchtest du? Die Prüfungsordnung bietet diese an:"), List.of(), choices);
		}
		int semesters = semesters(r.message()).orElse(p.regularSemesters());
		String start = (r.studyStart() != null) ? r.studyStart() : currentTerm(r.now().toLocalDate());
		StudyPlan plan = new StudyPlanner(p, this.programmes.history())
			.plan(new PlanRequest(focus, start, semesters, r.completedModules(), List.of(), null));

		List<Card> cards = new ArrayList<>();
		for (StudyPlan.Semester s : plan.semesters()) {
			String title = r.say("Semester %d · %s · %d credits", "Semester %d · %s · %d CP")
				.formatted(s.number(), termLabel(s.term(), r), s.credits());
			String modules = s.modules().stream().map((m) -> name(m.title(), r)).reduce((a, b) -> a + " · " + b).orElse("–");
			cards.add(new Card(title, modules, null, null));
		}
		String focusTitle = shortTitle(name(p.group(focus).title(), r));
		String text = r.say("Here's a plan for **%d credits in %d semesters** with the focus **%s**, starting %s.",
				"Hier ist ein Plan für **%d CP in %d Semestern** mit dem Schwerpunkt **%s**, ab %s.")
			.formatted(plan.credits(), semesters, focusTitle, termLabel(start, r));
		if (!plan.warnings().isEmpty()) {
			text += r.say(" There are %d things to check; open the planner to see them.", " Es gibt %d Punkte zu prüfen; öffne den Planer, um sie zu sehen.")
				.formatted(plan.warnings().size());
		}
		Action open = new Action("OPEN_PLANNER", r.say("Open in planner", "Im Planer öffnen"), null, null, null,
				Map.of("programme", p.id(), "focus", focus, "semesters", String.valueOf(semesters), "start", start));
		return AgentReply.of(ID, text, cards, List.of(open,
				Action.ask(r.say("Plan it in %d semesters instead", "Plane es stattdessen in %d Semestern").formatted(semesters + 1))));
	}

	/** The focus option whose title matches words in the message, e.g. "CIDA" or "Software". */
	static Optional<String> focus(String message, Programme p) {
		String m = message.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, List<String>> e : FOCUS_WORDS.entrySet()) {
			if (e.getValue().stream().anyMatch(m::contains)) {
				return p.focusOptions()
					.stream()
					.filter((f) -> f.title().toLowerCase(Locale.ROOT).contains(e.getKey()))
					.map(Programme.Group::code)
					.findFirst();
			}
		}
		return Optional.empty();
	}

	static Optional<Integer> semesters(String message) {
		Matcher m = SEMESTERS.matcher(message);
		if (!m.find()) {
			return Optional.empty();
		}
		String n = m.group(1).toLowerCase(Locale.ROOT);
		int count = WORDS.containsKey(n) ? WORDS.get(n)
				: (n.equals("fünf") ? 5 : n.equals("sechs") ? 6 : n.equals("sieben") ? 7 : n.equals("acht") ? 8 : Integer.parseInt(n));
		return (count >= 2 && count <= 10) ? Optional.of(count) : Optional.empty();
	}

	/** "Schwerpunkt Softwareentwicklung (SW)" → "Softwareentwicklung (SW)". */
	static String shortTitle(String title) {
		return title.replaceFirst("^Schwerpunkt\\s+", "");
	}

	static String currentTerm(LocalDate today) {
		int month = today.getMonthValue();
		if (month >= 10) {
			return "WS" + today.getYear();
		}
		return (month <= 3) ? "WS" + (today.getYear() - 1) : "SS" + today.getYear();
	}

	/** "WS2026" → "winter 2026/27" (en) or "WiSe 2026/27" (de). */
	static String termLabel(String term, AgentRequest r) {
		if (term == null || term.length() < 6) {
			return "";
		}
		int year = Integer.parseInt(term.substring(2));
		if (term.startsWith("WS")) {
			String next = String.format("%02d", (year + 1) % 100);
			return r.say("winter %d/%s", "WiSe %d/%s").formatted(year, next);
		}
		return r.say("summer %d", "SoSe %d").formatted(year);
	}

}
