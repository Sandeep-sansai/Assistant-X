package de.unikassel.assistant.agent;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * The one assistant students talk to. It hands each message to the specialist agent that fits
 * best and falls back to the regulations agent, which answers from the university's pages.
 *
 * Routing is rule-based for now: the local model is not reliable at picking tools. With Claude
 * this becomes a tool-use call over the same agents (docs/decisions.md #17).
 */
@Service
public class Concierge {

	static final String ID = "concierge";

	/** Below this, a specialist is not confident enough and the question goes to the regulations agent. */
	static final double THRESHOLD = 0.5;

	private static final Pattern HELP = Pattern.compile(
			"^\\s*(help|hilfe|hi|hello|hallo|hey)\\s*[!?.]*\\s*$|what can you do|how can you help|was kannst du|wie kannst du (mir )?helfen",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

	private final List<Agent> specialists;

	private final RegulationsAgent regulations;

	Concierge(List<Agent> agents, RegulationsAgent regulations) {
		this.specialists = agents.stream().filter((a) -> a != regulations).toList();
		this.regulations = regulations;
	}

	public AgentReply handle(AgentRequest request) {
		if (HELP.matcher(request.message()).find()) {
			return help(request);
		}
		return route(request).handle(request);
	}

	/** The agent a message goes to. */
	Agent route(AgentRequest request) {
		return this.specialists.stream()
			.filter((a) -> a.relevance(request) >= THRESHOLD)
			.max(Comparator.comparingDouble((Agent a) -> a.relevance(request)))
			.orElse(this.regulations);
	}

	private static AgentReply help(AgentRequest r) {
		String text = r.say("""
				Hi! Ask me about deadlines, your first weeks, campus events or the exam rules. \
				I answer from the university's own pages and link the source, and I can remind you of things.""", """
				Hi! Frag mich nach Fristen, deinen ersten Wochen, Veranstaltungen auf dem Campus oder der \
				Prüfungsordnung. Ich antworte aus den Seiten der Uni, verlinke die Quelle und erinnere dich an Dinge.""");
		List<AgentReply.Action> examples = r.german()
				? List.of(AgentReply.Action.ask("Erinnere mich am Freitag daran, den Semesterbeitrag zu überweisen"),
						AgentReply.Action.ask("Was steht als Nächstes an?"),
						AgentReply.Action.ask("Was ist diese Woche los?"),
						AgentReply.Action.ask("Was steht noch auf meiner Checkliste?"))
				: List.of(AgentReply.Action.ask("Remind me to pay the semester fee on Friday"),
						AgentReply.Action.ask("What's coming up?"), AgentReply.Action.ask("What's on this week?"),
						AgentReply.Action.ask("What's next on my checklist?"));
		return AgentReply.of(ID, text, List.of(), examples);
	}

}
