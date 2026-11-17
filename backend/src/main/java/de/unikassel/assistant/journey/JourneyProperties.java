package de.unikassel.assistant.journey;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Guided step-by-step flows from {@code journeys.yml}, offered by the assistant when they become
 * relevant for a student. Every step is checked by hand against its source page, like the key
 * dates (docs/decisions.md #13).
 */
@ConfigurationProperties("assistant.journeys")
public record JourneyProperties(List<Journey> items) {

	public JourneyProperties {
		items = items == null ? List.of() : items;
	}

	/**
	 * @param minSemester first semester (counted from the study start) the journey is offered in
	 * @param maxSemester last semester it is offered in
	 * @param daysBeforeStart how long before the semester start it becomes active
	 * @param daysAfterStart how long after the semester start it stays active
	 * @param prompt what the assistant asks; "{n}" is replaced with the number of steps
	 */
	public record Journey(String id, int minSemester, int maxSemester, int daysBeforeStart, int daysAfterStart,
			String title, String prompt, Texts de, List<Step> steps) {

		public Journey {
			steps = steps == null ? List.of() : steps;
		}

	}

	public record Texts(String title, String prompt) {
	}

	/**
	 * @param department only shown to students of this department; null for everyone
	 * @param replaces id of a general step this department-specific step stands in for
	 * @param ask question to open the chat with
	 * @param link the official page that explains the step
	 * @param actionLink where the step is actually done (e.g. the university's account activation),
	 * if that is a different official page; null otherwise
	 * @param actionLabel button text for {@code actionLink}
	 */
	public record Step(String id, String department, String replaces, String title, String detail, String ask,
			String link, String actionLink, String actionLabel, @DateTimeFormat(iso = ISO.DATE) LocalDate verifiedOn,
			StepTexts de) {
	}

	public record StepTexts(String title, String detail, String ask, String actionLabel) {
	}

}
