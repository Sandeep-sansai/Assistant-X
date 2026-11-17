package de.unikassel.assistant.agent;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import de.unikassel.assistant.agent.AgentReply.Action;
import de.unikassel.assistant.agent.AgentReply.Card;
import de.unikassel.assistant.journey.JourneyService;
import de.unikassel.assistant.journey.JourneyService.ActiveJourney;
import de.unikassel.assistant.journey.JourneyService.ActiveStep;

import org.springframework.stereotype.Component;

/** Walks the student through their active journey ("Your first weeks" and later ones). */
@Component
class OnboardingAgent implements Agent {

	static final String ID = "onboarding";

	private static final Pattern ASKS = Pattern.compile(
			"checklist|checkliste|first weeks|ersten wochen|first semester|erstsemester|next steps?|nächsten? schritte?"
					+ "|what should i do (?:first|next)|what(?:'s| is) next on|getting started|where do i start|new student"
					+ "|was (?:muss|sollte) ich (?:als erstes|zuerst|als nächstes|noch)|wo fange ich an|was steht noch auf",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

	private final JourneyService journeys;

	OnboardingAgent(JourneyService journeys) {
		this.journeys = journeys;
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
		List<ActiveJourney> active = this.journeys.active(r.department(), r.studyStart(), r.now().toLocalDate(),
				r.german() ? "de" : "en");
		if (active.isEmpty()) {
			String text = (r.studyStart() == null)
					? r.say("Tell me when you started studying (in your profile) and I'll show you the checklists that fit your semester.",
							"Sag mir in deinem Profil, wann du angefangen hast zu studieren, dann zeige ich dir die passenden Checklisten.")
					: r.say("There's no checklist for you right now. I'll bring one up when something is due, like your re-registration.",
							"Gerade gibt es keine Checkliste für dich. Ich melde mich, sobald etwas ansteht, etwa die Rückmeldung.");
			return AgentReply.of(ID, text);
		}
		ActiveJourney j = active.getFirst();
		Set<String> done = Set.copyOf(r.journeyDone().getOrDefault(j.id(), List.of()));
		List<ActiveStep> open = j.steps().stream().filter((s) -> !done.contains(s.id())).toList();
		int finished = j.steps().size() - open.size();
		Action openChecklist = new Action("OPEN_JOURNEY", r.say("Open checklist", "Checkliste öffnen"), j.title(), null, j.id());
		if (open.isEmpty()) {
			return AgentReply.of(ID, r.say("You've done everything in \"%s\". Well done! 🎉".formatted(j.title()),
					"Du hast alles aus „%s“ erledigt. Stark! 🎉".formatted(j.title())), List.of(), List.of(openChecklist));
		}
		String text = r.say("You've done %d of %d in \"%s\". Next up:".formatted(finished, j.steps().size(), j.title()),
				"Du hast %d von %d aus „%s“ erledigt. Als Nächstes:".formatted(finished, j.steps().size(), j.title()));
		List<Card> cards = open.stream().limit(3).map((s) -> new Card(s.title(), s.detail(), s.link(), null)).toList();
		return AgentReply.of(ID, text, cards, List.of(openChecklist, Action.ask(open.getFirst().ask())));
	}

}
