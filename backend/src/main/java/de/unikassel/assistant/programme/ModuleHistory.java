package de.unikassel.assistant.programme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Past offerings and results per module, from the anonymised, aggregated data the university can
 * provide (docs/past-data-request.md). Columns: module_code, semester ("WS 2024/25" or "SS 2025"),
 * offered (yes/no), enrolled, passed, avg_grade. Only totals; no personal data.
 */
public record ModuleHistory(String code, List<Term> terms) {

	/** @param passed null when the university did not provide it */
	public record Term(String semester, boolean winter, boolean offered, Integer enrolled, Integer passed, Double avgGrade) {
	}

	/** Winter/summer as seen in the data: only terms the module was actually offered in count. */
	public Programme.Offered offered() {
		boolean winter = this.terms.stream().anyMatch((t) -> t.offered() && t.winter());
		boolean summer = this.terms.stream().anyMatch((t) -> t.offered() && !t.winter());
		if (winter && summer) {
			return Programme.Offered.BOTH;
		}
		if (winter || summer) {
			return winter ? Programme.Offered.WINTER : Programme.Offered.SUMMER;
		}
		return Programme.Offered.UNKNOWN;
	}

	/** Share of enrolled students who passed, over all terms with both numbers; null without data. */
	public Double passRate() {
		int enrolled = 0;
		int passed = 0;
		for (Term t : this.terms) {
			if (t.enrolled() != null && t.passed() != null && t.enrolled() > 0) {
				enrolled += t.enrolled();
				passed += t.passed();
			}
		}
		return (enrolled == 0) ? null : (double) passed / enrolled;
	}

	/** Reads the CSV; a missing or empty path means no history. Rows that cannot be read are skipped. */
	static Map<String, ModuleHistory> load(String file) throws IOException {
		if (file == null || file.isBlank() || !Files.exists(Path.of(file))) {
			return Map.of();
		}
		Map<String, List<Term>> terms = new HashMap<>();
		List<String> lines = Files.readAllLines(Path.of(file), StandardCharsets.UTF_8);
		for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
			String[] c = line.split("[;,]", -1);
			if (c.length < 3 || c[0].isBlank()) {
				continue;
			}
			String semester = c[1].strip();
			boolean offered = c[2].strip().toLowerCase(Locale.ROOT).matches("yes|ja|true|1|x");
			terms.computeIfAbsent(c[0].strip(), (k) -> new ArrayList<>())
				.add(new Term(semester, semester.toUpperCase(Locale.ROOT).startsWith("WS"), offered, integer(c, 3),
						integer(c, 4), decimal(c, 5)));
		}
		Map<String, ModuleHistory> history = new HashMap<>();
		terms.forEach((code, list) -> history.put(code, new ModuleHistory(code, List.copyOf(list))));
		return history;
	}

	private static Integer integer(String[] c, int i) {
		try {
			return (c.length > i && !c[i].isBlank()) ? Integer.valueOf(c[i].strip()) : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Double decimal(String[] c, int i) {
		try {
			return (c.length > i && !c[i].isBlank()) ? Double.valueOf(c[i].strip().replace(',', '.')) : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
