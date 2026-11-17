package de.unikassel.assistant.agent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import de.unikassel.assistant.rag.AnswerService.Source;

/**
 * What an agent answers: text, optional cards (dates, events, steps) and optional actions the
 * student can take with one tap. Agents only suggest actions; nothing happens until the student
 * confirms (docs/decisions.md #17).
 *
 * @param questionId set when the answer was logged and can get feedback
 */
public record AgentReply(String agent, String text, List<Card> cards, List<Action> actions, List<Source> sources,
		Long questionId) {

	static AgentReply of(String agent, String text) {
		return new AgentReply(agent, text, List.of(), List.of(), List.of(), null);
	}

	static AgentReply of(String agent, String text, List<Card> cards, List<Action> actions) {
		return new AgentReply(agent, text, cards, actions, List.of(), null);
	}

	/** @param detail a short second line, e.g. "FB 16 · in 3 days" */
	public record Card(String title, String detail, String url, LocalDate date) {
	}

	/**
	 * @param type REMINDER (save a reminder), CALENDAR (download it as .ics), OPEN_JOURNEY (open a
	 * checklist), OPEN_PLANNER (open the study planner with {@code params}) or ASK (send
	 * {@code label} as the next message)
	 */
	public record Action(String type, String label, String title, LocalDateTime at, String journeyId,
			java.util.Map<String, String> params) {

		Action(String type, String label, String title, LocalDateTime at, String journeyId) {
			this(type, label, title, at, journeyId, java.util.Map.of());
		}

		static Action ask(String question) {
			return new Action("ASK", question, null, null, null);
		}

	}

}
