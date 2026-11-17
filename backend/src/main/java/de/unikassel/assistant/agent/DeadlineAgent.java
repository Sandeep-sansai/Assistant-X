package de.unikassel.assistant.agent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import de.unikassel.assistant.agent.AgentReply.Action;
import de.unikassel.assistant.agent.AgentReply.Card;
import de.unikassel.assistant.agent.AgentRequest.Reminder;
import de.unikassel.assistant.calendar.CalendarService;
import de.unikassel.assistant.calendar.CalendarService.Status;
import de.unikassel.assistant.calendar.CalendarService.Upcoming;

import org.springframework.stereotype.Component;

/** Keeps track of key dates and the student's own reminders, and sets new reminders on request. */
@Component
class DeadlineAgent implements Agent {

	static final String ID = "deadlines";

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern REMIND = Pattern.compile("remind me|set (?:a |me a )?reminder|erinnere? mich|erinnerung", FLAGS);

	private static final Pattern OVERVIEW = Pattern.compile(
			"what(?:'s| is| are)? (?:due|coming up|next|my deadlines)|upcoming (?:deadlines|dates)|my deadlines|key dates|what do i have to do"
					+ "|was steht (?:an|als nächstes an|bevor)|anstehende (?:fristen|termine)|welche fristen|meine fristen|wichtige termine",
			FLAGS);

	/** "remind me to", "erinnere mich daran, dass" and the like, at the start of a request. */
	private static final Pattern TRIGGER = Pattern.compile(
			"^\\s*(?:please\\s+|bitte\\s+)?(?:(?:can|could) you\\s+)?(?:remind me|set (?:a |me a )?reminder|erinnere? mich|erinnerung)\\s*(?:to|about|that|of|for)?\\b",
			FLAGS);

	/** Leftover joining words at either end of a reminder title. */
	private static final Pattern EDGE_WORDS = Pattern.compile(
			"^(?:[\\s,.:;!?]|\\b(?:to|about|that|on|at|by|daran|dass|an|am|um|bis|bitte|please)\\b)+|(?:[\\s,.:;!?]|\\b(?:on|at|by|am|um|bis|bitte|please)\\b)+$",
			FLAGS);

	private final CalendarService calendar;

	DeadlineAgent(CalendarService calendar) {
		this.calendar = calendar;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public double relevance(AgentRequest r) {
		if (REMIND.matcher(r.message()).find()) {
			return 1.0;
		}
		return OVERVIEW.matcher(r.message()).find() ? 0.8 : 0;
	}

	@Override
	public AgentReply handle(AgentRequest r) {
		return REMIND.matcher(r.message()).find() ? reminder(r) : overview(r);
	}

	private AgentReply reminder(AgentRequest r) {
		Optional<WhenParser.When> when = WhenParser.parse(r.message(), r.now().toLocalDate());
		if (when.isEmpty()) {
			return AgentReply.of(ID, r.say(
					"When should I remind you? Tell me the day, for example \"remind me to pay the semester fee on Friday at 10\".",
					"Wann soll ich dich erinnern? Nenn mir den Tag, zum Beispiel „Erinnere mich am Freitag um 10 daran, den Semesterbeitrag zu überweisen“."));
		}
		LocalDateTime at = when.get().at();
		if (at.isBefore(r.now())) {
			return AgentReply.of(ID, r.say("That time has already passed. When should I remind you instead?",
					"Dieser Zeitpunkt ist schon vorbei. Wann soll ich dich stattdessen erinnern?"));
		}
		String title = title(when.get().without(r.message()), r);
		String date = format(at, r);
		String text = r.german() ? germanQuestion(title, date)
				: "Shall I remind you to **%s** on %s?".formatted(lowerFirst(title), date);
		List<Action> actions = List.of(
				new Action("REMINDER", r.say("Save reminder", "Erinnerung speichern"), title, at, null),
				new Action("CALENDAR", r.say("Add to my calendar", "In meinen Kalender"), title, at, null));
		return AgentReply.of(ID, text, List.of(), actions);
	}

	private AgentReply overview(AgentRequest r) {
		List<Card> cards = new ArrayList<>();
		for (Reminder reminder : r.reminders()) {
			if (!reminder.done() && reminder.at() != null && !reminder.at().isBefore(r.now().minusDays(1))) {
				cards.add(new Card("⏰ " + reminder.title(), format(reminder.at(), r), null, reminder.at().toLocalDate()));
			}
		}
		for (Upcoming u : this.calendar.upcoming(r.department(), r.now().toLocalDate(), 4, r.german() ? "de" : "en")) {
			cards.add(new Card(u.title(), countdown(u, r), u.sourceUrl(), u.keyDate()));
		}
		cards.sort((a, b) -> a.date().compareTo(b.date()));
		if (cards.isEmpty()) {
			return AgentReply.of(ID, r.say("Nothing is coming up right now.", "Gerade steht nichts an."));
		}
		return AgentReply.of(ID, r.say("Here's what's coming up for you:", "Das steht als Nächstes für dich an:"),
				cards.stream().limit(6).toList(),
				List.of(Action.ask(r.say("Remind me about something", "Erinnere mich an etwas"))));
	}

	/** The reminder text: the request without the "remind me to" and the date phrases. */
	static String title(String rest, AgentRequest r) {
		String title = TRIGGER.matcher(rest).replaceFirst(" ");
		title = title.replaceAll("\\s+", " ").strip();
		String previous;
		do {
			previous = title;
			title = EDGE_WORDS.matcher(title).replaceAll("").strip();
		}
		while (!title.equals(previous));
		if (title.isEmpty()) {
			return r.say("Your reminder", "Deine Erinnerung");
		}
		return Character.toUpperCase(title.charAt(0)) + title.substring(1);
	}

	/** "…daran erinnern, die Karte abzuholen?" for a zu-infinitive, otherwise "…an die Bibliothek erinnern?". */
	static String germanQuestion(String title, String date) {
		String phrase = GERMAN_ARTICLE.matcher(title).lookingAt() ? lowerFirst(title) : title;
		if (ZU_INFINITIVE.matcher(phrase).find()) {
			return "Soll ich dich am %s daran erinnern, **%s**?".formatted(date, phrase);
		}
		return "Soll ich dich am %s an **%s** erinnern?".formatted(date, phrase);
	}

	private static final Pattern GERMAN_ARTICLE = Pattern.compile("(?:der|die|das|den|dem|des|ein|eine|einen|einem|mein|meine|meinen|meinem)\\b",
			FLAGS);

	/** "zu überweisen", "abzuholen", "anzumelden": the request is phrased as "daran, … zu tun". */
	private static final Pattern ZU_INFINITIVE = Pattern.compile("\\bzu\\s+\\p{L}+$|\\p{L}+zu\\p{L}+en$", FLAGS);

	private static String countdown(Upcoming u, AgentRequest r) {
		long n = u.daysLeft();
		String days = (n == 1) ? r.say("1 day", "1 Tag") : r.say(n + " days", n + " Tagen");
		if (n == 0) {
			return (u.status() == Status.ONGOING && u.end() != null) ? r.say("ends today", "endet heute") : r.say("today", "heute");
		}
		if (u.status() == Status.ONGOING) {
			return r.say("ends in " + days, "endet in " + days);
		}
		return (u.end() != null) ? r.say("starts in " + days, "beginnt in " + days) : r.say("in " + days, "in " + days);
	}

	static String format(LocalDateTime at, AgentRequest r) {
		return r.german() ? at.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM 'um' HH:mm 'Uhr'", Locale.GERMAN))
				: at.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm", Locale.UK));
	}

	private static String lowerFirst(String s) {
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

}
