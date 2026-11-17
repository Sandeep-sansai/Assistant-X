package de.unikassel.assistant.planner;

import java.util.List;

/**
 * A study plan with its check results. Warnings and notes are codes with parameters so the page
 * can show them in the student's language.
 *
 * @param completed modules the student already passed; they count but are not scheduled
 */
public record StudyPlan(String programme, String focus, String start, int credits, int requiredCredits,
		List<Semester> semesters, List<Requirement> requirements, List<Warning> warnings, List<Note> notes,
		List<String> completed) {

	/** @param term e.g. "WS2026" */
	public record Semester(int number, String term, boolean winter, int credits, List<Planned> modules) {
	}

	/** @param requirement the requirement code this module counts for, or null if it counts for none */
	public record Planned(String code, String title, int credits, String requirement, String offered, boolean oralExam,
			boolean thesis, String url) {
	}

	public record Requirement(String code, String title, int required, int planned, boolean met) {
	}

	/**
	 * @param type WRONG_TERM, UNKNOWN_TERM, HEAVY_SEMESTER, THESIS_TOO_EARLY, REQUIREMENT_UNMET,
	 * NO_ORAL_EXAM or NOT_OFFERED_RECENTLY
	 */
	public record Warning(String type, List<String> params) {

		static Warning of(String type, String... params) {
			return new Warning(type, List.of(params));
		}

	}

	/** @param type MENTORING or THESIS_WEEKS */
	public record Note(String type, List<String> params) {

		static Note of(String type, String... params) {
			return new Note(type, List.of(params));
		}

	}

}
