package de.unikassel.assistant.journey;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.unikassel.assistant.journey.JourneyProperties.Journey;
import de.unikassel.assistant.journey.JourneyProperties.Step;

import org.springframework.stereotype.Service;

/** Picks the journeys that fit a student's semester and the time of year. */
@Service
public class JourneyService {

	/** A study start such as "WS2026" (winter semester 2026/27) or "SS2027". */
	private static final Pattern TERM = Pattern.compile("(WS|SS)(\\d{4})");

	private final List<Journey> journeys;

	JourneyService(JourneyProperties config) {
		this.journeys = config.items();
	}

	/**
	 * @param studyStart the term the student started in, e.g. "WS2026"; journeys depend on it, so
	 * null or an unreadable value returns nothing
	 * @param language "en" or "de"
	 */
	public List<ActiveJourney> active(String department, String studyStart, LocalDate today, String language) {
		LocalDate started = parseTerm(studyStart);
		if (started == null) {
			return List.of();
		}
		LocalDate current = termStart(today);
		// Before a semester starts, journeys for it are already offered ("days before start").
		List<LocalDate> terms = List.of(current, nextTermStart(current));
		return this.journeys.stream()
			.filter((j) -> terms.stream().anyMatch((term) -> applies(j, semesterIn(term, started), term, today)))
			.map((j) -> localize(j, department, "de".equals(language)))
			.filter((j) -> !j.steps().isEmpty())
			.toList();
	}

	private static boolean applies(Journey j, int semester, LocalDate termStart, LocalDate today) {
		return semester >= j.minSemester() && semester <= j.maxSemester()
				&& !today.isBefore(termStart.minusDays(j.daysBeforeStart()))
				&& !today.isAfter(termStart.plusDays(j.daysAfterStart()));
	}

	/** Semester number in the term starting on {@code termStart} for someone who started on {@code started}. */
	static int semesterIn(LocalDate termStart, LocalDate started) {
		return termIndex(termStart) - termIndex(started) + 1;
	}

	private static int termIndex(LocalDate termStart) {
		return termStart.getYear() * 2 + ((termStart.getMonthValue() >= Month.OCTOBER.getValue()) ? 1 : 0);
	}

	static LocalDate parseTerm(String term) {
		if (term == null) {
			return null;
		}
		Matcher m = TERM.matcher(term);
		if (!m.matches()) {
			return null;
		}
		int year = Integer.parseInt(m.group(2));
		return "WS".equals(m.group(1)) ? LocalDate.of(year, Month.OCTOBER, 1) : LocalDate.of(year, Month.APRIL, 1);
	}

	private static LocalDate nextTermStart(LocalDate termStart) {
		return (termStart.getMonth() == Month.OCTOBER) ? LocalDate.of(termStart.getYear() + 1, Month.APRIL, 1)
				: LocalDate.of(termStart.getYear(), Month.OCTOBER, 1);
	}

	/**
	 * The start of the semester today belongs to: 1 October for the winter semester, 1 April for
	 * the summer semester.
	 */
	static LocalDate termStart(LocalDate today) {
		int month = today.getMonthValue();
		if (month >= Month.OCTOBER.getValue()) {
			return LocalDate.of(today.getYear(), Month.OCTOBER, 1);
		}
		if (month <= Month.MARCH.getValue()) {
			return LocalDate.of(today.getYear() - 1, Month.OCTOBER, 1);
		}
		return LocalDate.of(today.getYear(), Month.APRIL, 1);
	}

	private static ActiveJourney localize(Journey j, String department, boolean german) {
		List<Step> forDepartment = j.steps()
			.stream()
			.filter((s) -> s.department() == null || s.department().equals(department))
			.toList();
		// A department-specific step replaces the general one it stands in for.
		Set<String> replaced = forDepartment.stream()
			.map(Step::replaces)
			.filter((id) -> id != null)
			.collect(Collectors.toSet());
		List<ActiveStep> steps = forDepartment.stream()
			.filter((s) -> !replaced.contains(s.id()))
			.map((s) -> localize(s, german))
			.toList();
		String title = (german && j.de() != null && j.de().title() != null) ? j.de().title() : j.title();
		String prompt = (german && j.de() != null && j.de().prompt() != null) ? j.de().prompt() : j.prompt();
		return new ActiveJourney(j.id(), title, prompt.replace("{n}", String.valueOf(steps.size())), steps);
	}

	private static ActiveStep localize(Step s, boolean german) {
		boolean de = german && s.de() != null;
		return new ActiveStep(s.id(), (de && s.de().title() != null) ? s.de().title() : s.title(),
				(de && s.de().detail() != null) ? s.de().detail() : s.detail(),
				(de && s.de().ask() != null) ? s.de().ask() : s.ask(), s.link(), s.actionLink(),
				(de && s.de().actionLabel() != null) ? s.de().actionLabel() : s.actionLabel(), s.verifiedOn());
	}

	public record ActiveJourney(String id, String title, String prompt, List<ActiveStep> steps) {
	}

	/**
	 * @param link the official page that explains the step
	 * @param actionLink where the step is done, if that is a different official page
	 */
	public record ActiveStep(String id, String title, String detail, String ask, String link, String actionLink,
			String actionLabel, LocalDate verifiedOn) {
	}

}
