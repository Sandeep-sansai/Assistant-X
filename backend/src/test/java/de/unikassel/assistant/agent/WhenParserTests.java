package de.unikassel.assistant.agent;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhenParserTests {

	/** A Wednesday. */
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	private static LocalDateTime at(String text) {
		return WhenParser.parse(text, TODAY).map(WhenParser.When::at).orElse(null);
	}

	@Test
	void readsRelativeDays() {
		assertThat(at("remind me tomorrow")).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 0));
		assertThat(at("erinnere mich heute um 18 Uhr")).isEqualTo(LocalDateTime.of(2026, 10, 14, 18, 0));
		assertThat(at("the day after tomorrow at 2pm")).isEqualTo(LocalDateTime.of(2026, 10, 16, 14, 0));
		assertThat(at("übermorgen")).isEqualTo(LocalDateTime.of(2026, 10, 16, 9, 0));
	}

	@Test
	void readsWeekdaysAsTheNextOne() {
		assertThat(at("remind me to pay the fee on Friday")).isEqualTo(LocalDateTime.of(2026, 10, 16, 9, 0));
		assertThat(at("am Montag um 10:30")).isEqualTo(LocalDateTime.of(2026, 10, 19, 10, 30));
		// Today is Wednesday, so "Wednesday" means next week's.
		assertThat(at("on wednesday")).isEqualTo(LocalDateTime.of(2026, 10, 21, 9, 0));
		assertThat(at("next week")).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
	}

	@Test
	void readsCountsOfDaysAndWeeks() {
		assertThat(at("in 3 days")).isEqualTo(LocalDateTime.of(2026, 10, 17, 9, 0));
		assertThat(at("in a week")).isEqualTo(LocalDateTime.of(2026, 10, 21, 9, 0));
		assertThat(at("in zwei Tagen um 8 Uhr")).isEqualTo(LocalDateTime.of(2026, 10, 16, 8, 0));
	}

	@Test
	void readsCalendarDatesInBothLanguages() {
		assertThat(at("on 20 October at 14:00")).isEqualTo(LocalDateTime.of(2026, 10, 20, 14, 0));
		assertThat(at("October 3rd")).isEqualTo(LocalDateTime.of(2027, 10, 3, 9, 0));
		assertThat(at("am 20.10. um 14 Uhr")).isEqualTo(LocalDateTime.of(2026, 10, 20, 14, 0));
		assertThat(at("bis 1. Dezember")).isEqualTo(LocalDateTime.of(2026, 12, 1, 9, 0));
		assertThat(at("am 15.01.2027")).isEqualTo(LocalDateTime.of(2027, 1, 15, 9, 0));
	}

	@Test
	void findsNothingWithoutADay() {
		assertThat(at("remind me to pay the fee")).isNull();
		assertThat(at("at 10")).isNull();
	}

	@Test
	void cutsTheDatePhrasesOut() {
		String text = "remind me to pay the semester fee on Friday at 10";
		WhenParser.When when = WhenParser.parse(text, TODAY).orElseThrow();
		assertThat(when.without(text).replaceAll("\\s+", " ").strip()).isEqualTo("remind me to pay the semester fee");
	}

}
