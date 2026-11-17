package de.unikassel.assistant.planner;

import java.util.List;

/**
 * What the student wants: focus, start, how many semesters, and what they already have or prefer.
 *
 * @param focus a focus option code from the regulations, e.g. "8.1"
 * @param start the first semester of the plan, e.g. "WS2026"
 * @param semesters number of semesters including the thesis semester
 * @param maxCreditsPerSemester optional upper bound for a comfortable load
 */
public record PlanRequest(String focus, String start, int semesters, List<String> completed, List<String> preferred,
		Integer maxCreditsPerSemester) {

	public PlanRequest {
		completed = (completed == null) ? List.of() : completed;
		preferred = (preferred == null) ? List.of() : preferred;
	}

}
