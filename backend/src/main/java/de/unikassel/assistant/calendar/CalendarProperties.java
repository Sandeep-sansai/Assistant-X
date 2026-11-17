package de.unikassel.assistant.calendar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Hand-checked academic dates from {@code academic-calendar.yml}. Dates are curated rather than
 * extracted automatically because a wrong deadline can cost a student a semester
 * (docs/decisions.md #5 and #13).
 */
@ConfigurationProperties("assistant.calendar")
public record CalendarProperties(List<Entry> entries) {

	public CalendarProperties {
		entries = entries == null ? List.of() : entries;
	}

	/**
	 * @param end last day of a period, or null for a single date
	 * @param department department id, or null if it applies to everyone
	 * @param action what the student should do, shown as a to-do; null for plain information
	 * @param suggestions questions the assistant offers while this date is coming up
	 * @param de German texts; any field left out falls back to the English one
	 */
	public record Entry(String id, String title, @DateTimeFormat(iso = ISO.DATE) LocalDate start,
			@DateTimeFormat(iso = ISO.DATE) LocalDate end, String department, String action, String sourceUrl,
			@DateTimeFormat(iso = ISO.DATE) LocalDate verifiedOn, List<String> suggestions, Texts de) {

		public Entry {
			suggestions = suggestions == null ? List.of() : suggestions;
		}

		LocalDate last() {
			return (this.end != null) ? this.end : this.start;
		}

		/** The texts in the given language ("en" or "de"), falling back to English. */
		Texts texts(String language) {
			Texts en = new Texts(this.title, this.action, this.suggestions);
			if (!"de".equals(language) || this.de == null) {
				return en;
			}
			return new Texts((this.de.title() != null) ? this.de.title() : en.title(),
					(this.de.action() != null) ? this.de.action() : en.action(),
					!this.de.suggestions().isEmpty() ? this.de.suggestions() : en.suggestions());
		}

	}

	public record Texts(String title, String action, List<String> suggestions) {

		public Texts {
			suggestions = suggestions == null ? List.of() : suggestions;
		}

	}

}
