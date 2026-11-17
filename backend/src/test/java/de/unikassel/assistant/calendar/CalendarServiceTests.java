package de.unikassel.assistant.calendar;

import java.time.LocalDate;
import java.util.List;

import de.unikassel.assistant.calendar.CalendarProperties.Entry;
import de.unikassel.assistant.calendar.CalendarProperties.Texts;
import de.unikassel.assistant.calendar.CalendarService.Status;
import de.unikassel.assistant.calendar.CalendarService.Upcoming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

	private final CalendarService calendar = new CalendarService(new CalendarProperties(List.of(
			entry("past", "2026-06-01", "2026-09-01", null),
			entry("lectures", "2026-10-12", "2027-02-12", null),
			entry("christmas", "2026-12-19", "2027-01-10", null),
			entry("running", "2026-10-01", "2026-10-20", null),
			entry("fb16-only", "2026-11-02", null, "fb16"),
			entry("today", "2026-10-11", null, null))));

	@Test
	void findsTheLecturePeriodWithItsBreaks() {
		CalendarService service = new CalendarService(new CalendarProperties(List.of(
				entry("lectures-ws", "2026-10-12", "2027-02-12", null),
				entry("christmas-break", "2026-12-19", "2027-01-10", null),
				entry("lectures-ss", "2027-04-12", "2027-07-16", null))));

		CalendarService.Semester now = service.semester(TODAY, "en").orElseThrow();
		assertThat(now.lectureStart()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(now.breaks()).extracting(CalendarService.Break::start).containsExactly(LocalDate.of(2026, 12, 19));

		CalendarService.Semester spring = service.semester(LocalDate.of(2027, 3, 1), "en").orElseThrow();
		assertThat(spring.lectureStart()).isEqualTo(LocalDate.of(2027, 4, 12));
		assertThat(spring.breaks()).isEmpty();
	}

	@Test
	void ordersByTheNextRelevantDayAndDropsPastEntries() {
		List<Upcoming> upcoming = this.calendar.upcoming(null, TODAY, 10, "en");

		assertThat(upcoming).extracting(Upcoming::id).containsExactly("today", "lectures", "running", "christmas");
	}

	@Test
	void countsToTheStartBeforeAPeriodAndToTheEndDuringIt() {
		List<Upcoming> upcoming = this.calendar.upcoming(null, TODAY, 10, "en");

		Upcoming lectures = upcoming.get(1);
		assertThat(lectures.status()).isEqualTo(Status.UPCOMING);
		assertThat(lectures.daysLeft()).isEqualTo(1);
		Upcoming running = upcoming.get(2);
		assertThat(running.status()).isEqualTo(Status.ONGOING);
		assertThat(running.daysLeft()).isEqualTo(9);
		assertThat(upcoming.getFirst().status()).isEqualTo(Status.ONGOING);
		assertThat(upcoming.getFirst().daysLeft()).isZero();
	}

	@Test
	void includesDepartmentEntriesOnlyForThatDepartment() {
		assertThat(this.calendar.upcoming("fb16", TODAY, 10, "en")).extracting(Upcoming::id).contains("fb16-only");
		assertThat(this.calendar.upcoming("fb07", TODAY, 10, "en")).extracting(Upcoming::id).doesNotContain("fb16-only");
	}

	@Test
	void respectsTheLimit() {
		assertThat(this.calendar.upcoming(null, TODAY, 2, "en")).hasSize(2);
	}

	@Test
	void usesGermanTextsWhereGivenAndEnglishOtherwise() {
		CalendarService translated = new CalendarService(new CalendarProperties(List.of(new Entry("lectures",
				"Lecture period", LocalDate.parse("2026-10-12"), null, null, "Sign up for courses", null, null,
				List.of("How do I sign up?"), new Texts("Vorlesungszeit", null, List.of("Wie melde ich mich an?"))))));

		Upcoming de = translated.upcoming(null, TODAY, 1, "de").getFirst();
		assertThat(de.title()).isEqualTo("Vorlesungszeit");
		assertThat(de.action()).isEqualTo("Sign up for courses");
		assertThat(de.suggestions()).containsExactly("Wie melde ich mich an?");
		assertThat(translated.upcoming(null, TODAY, 1, "en").getFirst().title()).isEqualTo("Lecture period");
	}

	private static Entry entry(String id, String start, String end, String department) {
		return new Entry(id, id, LocalDate.parse(start), (end != null) ? LocalDate.parse(end) : null, department, null,
				null, null, null, null);
	}

}
