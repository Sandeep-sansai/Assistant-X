package de.unikassel.assistant.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Pattern;

import de.unikassel.assistant.agent.AgentReply.Action;
import de.unikassel.assistant.agent.AgentReply.Card;
import de.unikassel.assistant.feed.FeedService;
import de.unikassel.assistant.feed.FeedService.Feed;
import de.unikassel.assistant.feed.FeedService.Item;

import org.springframework.stereotype.Component;

/** Finds events and news that fit the student: their department first, then the university. */
@Component
class CampusAgent implements Agent {

	static final String ID = "campus";

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	/** Asking what is on, rather than asking a question that merely mentions events. */
	private static final Pattern WHATS_ON = Pattern.compile(
			"what(?:'s| is) (?:on|happening|going on)|whats on|anything (?:on|happening)|was ist\\b.{0,40}\\b(?:los|geboten)\\b|was läuft|was geht", FLAGS);

	private static final Pattern EVENTS = Pattern.compile("\\bevents?\\b|veranstaltung", FLAGS);

	private static final Pattern WINDOW = Pattern.compile(
			"today|tonight|tomorrow|this week|next week|weekend|upcoming|any|are there|for me|heute|morgen|diese woche|nächste woche|wochenende|anstehend|gibt es|für mich",
			FLAGS);

	private static final Pattern NEWS = Pattern.compile("(?:latest|recent|any|new) news|\\bnews\\b.*\\?|neuigkeiten|was gibt'?s neues|aktuelles", FLAGS);

	private final FeedService feed;

	CampusAgent(FeedService feed) {
		this.feed = feed;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public double relevance(AgentRequest r) {
		String m = r.message();
		if (WHATS_ON.matcher(m).find() || (EVENTS.matcher(m).find() && WINDOW.matcher(m).find())) {
			return 0.85;
		}
		return NEWS.matcher(m).find() ? 0.7 : 0;
	}

	@Override
	public AgentReply handle(AgentRequest r) {
		LocalDate today = r.now().toLocalDate();
		Feed f = this.feed.feed(r.department(), today);
		if (NEWS.matcher(r.message()).find() && !EVENTS.matcher(r.message()).find()) {
			List<Card> news = f.news().stream().limit(5).map((i) -> card(i, null, r)).toList();
			return news.isEmpty() ? AgentReply.of(ID, r.say("No news right now.", "Gerade gibt es keine News."))
					: AgentReply.of(ID, r.say("The latest news for you:", "Die neuesten News für dich:"), news, List.of());
		}
		Window w = window(r.message().toLowerCase(), today, r);
		List<Card> events = f.events()
			.stream()
			.filter((i) -> !i.date().isBefore(w.from()) && !i.date().isAfter(w.to()))
			.limit(6)
			.map((i) -> card(i, today, r))
			.toList();
		if (events.isEmpty()) {
			return AgentReply.of(ID, r.say("Nothing is listed %s. Want me to look further ahead?".formatted(w.label()),
					"Für %s ist nichts eingetragen. Soll ich weiter vorausschauen?".formatted(w.label())), List.of(),
					List.of(Action.ask(r.say("What's on next week?", "Was ist nächste Woche los?"))));
		}
		return AgentReply.of(ID, r.say("Here's what's on %s:".formatted(w.label()), "Das ist %s los:".formatted(w.label())),
				events, List.of());
	}

	private static Card card(Item i, LocalDate today, AgentRequest r) {
		String when = "";
		if (today != null) {
			long n = ChronoUnit.DAYS.between(today, i.date());
			when = (n == 0) ? r.say("today", "heute") : (n == 1) ? r.say("tomorrow", "morgen") : r.say("in " + n + " days", "in " + n + " Tagen");
		}
		String detail = String.join(" · ", java.util.stream.Stream.of(i.source(), i.category(), when)
			.filter((s) -> s != null && !s.isBlank())
			.toList());
		return new Card(i.title(), detail, i.url(), i.date());
	}

	private static Window window(String m, LocalDate today, AgentRequest r) {
		if (m.contains("today") || m.contains("tonight") || m.contains("heute")) {
			return new Window(today, today, r.say("today", "heute"));
		}
		if (m.contains("tomorrow") || m.contains("morgen")) {
			return new Window(today.plusDays(1), today.plusDays(1), r.say("tomorrow", "morgen"));
		}
		if (m.contains("weekend") || m.contains("wochenende")) {
			LocalDate sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
			return new Window(sat, sat.plusDays(1), r.say("this weekend", "am Wochenende"));
		}
		if (m.contains("next week") || m.contains("nächste woche")) {
			LocalDate mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
			return new Window(mon, mon.plusDays(6), r.say("next week", "nächste Woche"));
		}
		return new Window(today, today.plusDays(7), r.say("in the next 7 days", "in den nächsten 7 Tagen"));
	}

	private record Window(LocalDate from, LocalDate to, String label) {
	}

}
