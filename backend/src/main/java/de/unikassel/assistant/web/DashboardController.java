package de.unikassel.assistant.web;

import java.time.LocalDate;
import java.util.List;

import de.unikassel.assistant.calendar.CalendarService;
import de.unikassel.assistant.calendar.CalendarService.Upcoming;
import de.unikassel.assistant.config.UniversityProperties;
import de.unikassel.assistant.feed.FeedProperties;
import de.unikassel.assistant.feed.FeedService;
import de.unikassel.assistant.feed.FeedService.Feed;
import de.unikassel.assistant.feed.FeedService.Item;
import de.unikassel.assistant.i18n.Translator;
import de.unikassel.assistant.journey.JourneyService;
import de.unikassel.assistant.journey.JourneyService.ActiveJourney;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only data for the start page: profile options, news and events, upcoming dates. */
@RestController
@RequestMapping("/api")
class DashboardController {

	private final UniversityProperties university;

	private final FeedProperties feedConfig;

	private final FeedService feed;

	private final CalendarService calendar;

	private final JourneyService journeys;

	private final Translator translator;

	DashboardController(UniversityProperties university, FeedProperties feedConfig, FeedService feed,
			CalendarService calendar, JourneyService journeys, Translator translator) {
		this.university = university;
		this.feedConfig = feedConfig;
		this.feed = feed;
		this.calendar = calendar;
		this.journeys = journeys;
		this.translator = translator;
	}

	@GetMapping("/university")
	UniversityInfo university() {
		return new UniversityInfo(this.university.departments()
			.stream()
			.map((d) -> new DepartmentInfo(d.id(), d.name(), d.programmes(), this.feedConfig.covers(d.id())))
			.toList());
	}

	@GetMapping("/feed")
	Feed feed(@RequestParam(required = false) String department, @RequestParam(defaultValue = "en") String lang) {
		Feed feed = this.feed.feed(known(department), today());
		return "de".equals(lang) ? feed
				: new Feed(english(feed.news()), english(feed.events()), feed.departmentCovered());
	}

	/** University news is mostly German; headlines already translated are shown in English, the rest follow. */
	private List<Item> english(List<Item> items) {
		List<String> texts = new java.util.ArrayList<>();
		for (Item i : items) {
			texts.add(i.title());
			texts.addAll(categories(i.category()));
		}
		java.util.Map<String, String> en = this.translator.english(texts);
		return items.stream()
			.map((i) -> new Item(en.getOrDefault(i.title(), i.title()), i.url(), i.date(),
					(i.category() == null) ? null
							: String.join(" | ", categories(i.category()).stream().map((c) -> en.getOrDefault(c, c)).toList()),
					i.source(), i.department(), i.firstSeenAt()))
			.toList();
	}

	/** "Veranstaltung | Studium" → each label on its own, so they are translated once and reused. */
	private static List<String> categories(String category) {
		return (category == null) ? List.of()
				: java.util.Arrays.stream(category.split("\\|")).map(String::strip).filter((c) -> !c.isEmpty()).toList();
	}

	@GetMapping("/calendar")
	List<Upcoming> calendar(@RequestParam(required = false) String department,
			@RequestParam(defaultValue = "en") String lang) {
		return this.calendar.upcoming(known(department), today(), 8, "de".equals(lang) ? "de" : "en");
	}

	/** The current (or next) lecture period with its breaks, for the "this semester" card. */
	@GetMapping("/semester")
	org.springframework.http.ResponseEntity<CalendarService.Semester> semester(
			@RequestParam(defaultValue = "en") String lang) {
		return org.springframework.http.ResponseEntity.of(this.calendar.semester(today(), "de".equals(lang) ? "de" : "en"));
	}

	/** Guided journeys the assistant should offer now, based on the student's study start (e.g. WS2026). */
	@GetMapping("/journeys")
	List<ActiveJourney> journeys(@RequestParam(required = false) String department,
			@RequestParam(required = false) String start, @RequestParam(defaultValue = "en") String lang) {
		return this.journeys.active(known(department), start, today(), "de".equals(lang) ? "de" : "en");
	}

	/** Unknown ids are treated like "no department" rather than rejected. */
	private String known(String department) {
		return this.university.departments().stream().anyMatch((d) -> d.id().equals(department)) ? department : null;
	}

	private LocalDate today() {
		return LocalDate.now(this.university.zone());
	}

	record UniversityInfo(List<DepartmentInfo> departments) {
	}

	/** @param hasFeed whether the department has its own news or event sources */
	record DepartmentInfo(String id, String name, List<String> programmes, boolean hasFeed) {
	}

}
