package de.unikassel.assistant.calendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.unikassel.assistant.calendar.CalendarProperties.Entry;
import de.unikassel.assistant.calendar.CalendarProperties.Texts;

import org.springframework.stereotype.Service;

/** Selects the academic dates that are still ahead of a student, nearest first. */
@Service
public class CalendarService {

	private final List<Entry> entries;

	CalendarService(CalendarProperties config) {
		this.entries = config.entries();
	}

	/** @param language "en" or "de"; entries without German texts stay English */
	public List<Upcoming> upcoming(String department, LocalDate today, int limit, String language) {
		return this.entries.stream()
			.filter((e) -> e.start() != null && !e.last().isBefore(today))
			.filter((e) -> e.department() == null || e.department().equals(department))
			.map((e) -> upcoming(e, today, e.texts(language)))
			.sorted(Comparator.comparing(Upcoming::keyDate))
			.limit(limit)
			.toList();
	}

	/**
	 * The lecture period running today, or the next one, with its breaks. Lecture periods are the
	 * entries whose id starts with "lectures-"; breaks are those with "break" in their id.
	 */
	public Optional<Semester> semester(LocalDate today, String language) {
		return this.entries.stream()
			.filter((e) -> e.id().startsWith("lectures-") && e.start() != null && e.end() != null)
			.filter((e) -> !e.end().isBefore(today))
			.min(Comparator.comparing(Entry::start))
			.map((lectures) -> new Semester(lectures.texts(language).title(), lectures.start(), lectures.end(),
					this.entries.stream()
						.filter((e) -> e.id().contains("break") && e.start() != null)
						.filter((e) -> !e.start().isAfter(lectures.end()) && !e.last().isBefore(lectures.start()))
						.map((e) -> new Break(e.texts(language).title(), e.start(), e.last()))
						.toList(),
					lectures.sourceUrl()));
	}

	private static Upcoming upcoming(Entry e, LocalDate today, Texts texts) {
		boolean started = !e.start().isAfter(today);
		// Before a period starts, its start matters; once it runs, its end does.
		LocalDate key = started ? e.last() : e.start();
		return new Upcoming(e.id(), texts.title(), e.start(), e.end(), started ? Status.ONGOING : Status.UPCOMING,
				ChronoUnit.DAYS.between(today, key), key, texts.action(), e.sourceUrl(), e.verifiedOn(),
				texts.suggestions());
	}

	/** @param daysLeft days until the start (UPCOMING) or until the end (ONGOING) */
	public record Upcoming(String id, String title, LocalDate start, LocalDate end, Status status, long daysLeft,
			LocalDate keyDate, String action, String sourceUrl, LocalDate verifiedOn, List<String> suggestions) {
	}

	public record Semester(String title, LocalDate lectureStart, LocalDate lectureEnd, List<Break> breaks,
			String sourceUrl) {
	}

	public record Break(String title, LocalDate start, LocalDate end) {
	}

	public enum Status {

		UPCOMING, ONGOING

	}

}
