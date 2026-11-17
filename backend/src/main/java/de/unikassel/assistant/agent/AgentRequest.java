package de.unikassel.assistant.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One message to the assistant plus what the agents may know about the student. Until there are
 * accounts, the browser sends this state along (docs/decisions.md #12); afterwards the server
 * loads it.
 *
 * @param language "en" or "de"
 * @param studyStart e.g. "WS2026"
 * @param journeyDone finished step ids per journey id
 * @param reminders the student's own reminders
 * @param now the current time in the university's time zone
 * @param planFocus the focus option chosen in the study planner, e.g. "8.1"
 * @param completedModules module codes the student marked as passed in the study planner
 * @param plannedModules module codes planned for the current semester in the study planner
 * @param history the last few turns of this chat, oldest first, so follow-up questions make sense
 */
public record AgentRequest(String message, String language, String department, String departmentName,
		String programme, Integer semester, String studyStart, Map<String, List<String>> journeyDone,
		List<Reminder> reminders, LocalDateTime now, String planFocus, List<String> completedModules,
		List<String> plannedModules, List<Turn> history) {

	public AgentRequest {
		journeyDone = (journeyDone != null) ? journeyDone : Map.of();
		reminders = (reminders != null) ? reminders : List.of();
		completedModules = (completedModules != null) ? completedModules : List.of();
		plannedModules = (plannedModules != null) ? plannedModules : List.of();
		history = (history != null) ? history : List.of();
	}

	/** Without the current semester's plan and the chat history. */
	public AgentRequest(String message, String language, String department, String departmentName, String programme,
			Integer semester, String studyStart, Map<String, List<String>> journeyDone, List<Reminder> reminders,
			LocalDateTime now, String planFocus, List<String> completedModules) {
		this(message, language, department, departmentName, programme, semester, studyStart, journeyDone, reminders, now,
				planFocus, completedModules, null, null);
	}

	/** Without study planner state. */
	public AgentRequest(String message, String language, String department, String departmentName, String programme,
			Integer semester, String studyStart, Map<String, List<String>> journeyDone, List<Reminder> reminders,
			LocalDateTime now) {
		this(message, language, department, departmentName, programme, semester, studyStart, journeyDone, reminders, now,
				null, null, null, null);
	}

	boolean german() {
		return "de".equals(this.language);
	}

	/** The English or German text, depending on the student's language. */
	String say(String english, String german) {
		return german() ? german : english;
	}

	public record Reminder(String title, LocalDateTime at, boolean done) {
	}

	/** One earlier question and the assistant's answer. */
	public record Turn(String question, String answer) {
	}

}
