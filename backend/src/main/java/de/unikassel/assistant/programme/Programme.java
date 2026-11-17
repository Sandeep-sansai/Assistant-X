package de.unikassel.assistant.programme;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A degree programme as its examination regulations define it: the requirement groups (areas and
 * focus options with the credits each needs) and the modules that count towards them. Imported
 * from the university's module database (docs/decisions.md #18).
 *
 * @param thesisMinCredits credits needed before the thesis topic is given out
 * @param groups requirement groups in document order; their hierarchy is in the codes ("8.1.2" is
 * part of "8.1")
 */
public record Programme(String id, String title, int totalCredits, int regularSemesters, int thesisCredits,
		int thesisMinCredits, int thesisWeeks, String sourceUrl, Instant fetchedAt, List<Group> groups,
		Map<String, Module> modules) {

	/** Focus options ("Schwerpunkte") are numbered 8.1, 8.2, … in the regulations. */
	private static final Pattern FOCUS = Pattern.compile("8\\.\\d+");

	public List<Group> focusOptions() {
		return this.groups.stream().filter((g) -> FOCUS.matcher(g.code()).matches()).toList();
	}

	/** Groups directly below the given code, e.g. 8.1.1 – 8.1.3 for 8.1. */
	public List<Group> children(String code) {
		return this.groups.stream()
			.filter((g) -> g.code().startsWith(code + ".") && g.code().indexOf('.', code.length() + 1) < 0)
			.toList();
	}

	public Group group(String code) {
		return this.groups.stream().filter((g) -> g.code().equals(code)).findFirst().orElse(null);
	}

	/**
	 * @param credits credits this group requires, or null when only its parent sets a total
	 * @param modules module codes that count towards this group
	 */
	public record Group(String code, String title, Integer credits, List<String> modules) {
	}

	/**
	 * @param offered when the module is taught, read from "Häufigkeit des Angebotes"
	 * @param oralExam whether the exam can be oral (the regulations require at least one)
	 */
	public record Module(String code, String title, int credits, Offered offered, String offeredText,
			String language, int durationSemesters, boolean oralExam, String exam, String prerequisites, String url) {
	}

	public enum Offered {

		WINTER, SUMMER, BOTH, UNKNOWN;

		public boolean in(boolean winter) {
			return this == BOTH || this == UNKNOWN || (winter ? this == WINTER : this == SUMMER);
		}

	}

}
