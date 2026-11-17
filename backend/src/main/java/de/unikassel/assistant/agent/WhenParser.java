package de.unikassel.assistant.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a point in time in an English or German request such as "remind me to pay the fee on
 * Friday at 10" or "erinnere mich übermorgen um 14 Uhr". Deterministic on purpose: a reminder on
 * the wrong day is worse than asking again.
 */
final class WhenParser {

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Map<String, Integer> NUMBERS = Map.ofEntries(Map.entry("a", 1), Map.entry("an", 1),
			Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
			Map.entry("einem", 1), Map.entry("einer", 1), Map.entry("zwei", 2), Map.entry("drei", 3),
			Map.entry("vier", 4), Map.entry("fünf", 5));

	private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(Map.entry("monday", DayOfWeek.MONDAY),
			Map.entry("tuesday", DayOfWeek.TUESDAY), Map.entry("wednesday", DayOfWeek.WEDNESDAY),
			Map.entry("thursday", DayOfWeek.THURSDAY), Map.entry("friday", DayOfWeek.FRIDAY),
			Map.entry("saturday", DayOfWeek.SATURDAY), Map.entry("sunday", DayOfWeek.SUNDAY),
			Map.entry("montag", DayOfWeek.MONDAY), Map.entry("dienstag", DayOfWeek.TUESDAY),
			Map.entry("mittwoch", DayOfWeek.WEDNESDAY), Map.entry("donnerstag", DayOfWeek.THURSDAY),
			Map.entry("freitag", DayOfWeek.FRIDAY), Map.entry("samstag", DayOfWeek.SATURDAY),
			Map.entry("sonntag", DayOfWeek.SUNDAY));

	private static final Pattern DAY_AFTER_TOMORROW = Pattern.compile("\\b(?:the\\s+)?day after tomorrow\\b|\\bübermorgen\\b", FLAGS);

	private static final Pattern RELATIVE_DAY = Pattern.compile("\\b(today|heute|tomorrow|morgen)\\b", FLAGS);

	private static final Pattern IN_N = Pattern.compile(
			"\\bin\\s+(\\d{1,3}|a|an|one|two|three|four|five|einem|einer|zwei|drei|vier|fünf)\\s+(days?|weeks?|tagen?|wochen?)\\b",
			FLAGS);

	private static final Pattern NEXT_WEEK = Pattern.compile("\\b(?:next week|nächste[n]? woche)\\b", FLAGS);

	private static final Pattern WEEKDAY = Pattern.compile(
			"\\b(?:(?:on|next|this|am|nächsten|diesen|kommenden)\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\\b",
			FLAGS);

	private static final String MONTHS = "jan(?:uary|uar)?|feb(?:ruary|ruar)?|mar(?:ch)?|märz|apr(?:il)?|may|mai|jun(?:e|i)?|jul(?:y|i)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|okt(?:ober)?|nov(?:ember)?|dec(?:ember)?|dez(?:ember)?";

	private static final Pattern DAY_MONTH = Pattern.compile(
			"\\b(?:(?:on|am|by|bis)\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th|\\.)?\\s+(?:of\\s+)?(" + MONTHS + ")\\b\\.?",
			FLAGS);

	private static final Pattern MONTH_DAY = Pattern.compile(
			"\\b(?:(?:on|by)\\s+)?(" + MONTHS + ")\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", FLAGS);

	private static final Pattern NUMERIC_DATE = Pattern.compile(
			"\\b(?:(?:on|am|by|bis)\\s+)?(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})?", FLAGS);

	private static final Pattern TIME = Pattern.compile(
			"\\b(?:at|um|gegen)\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|uhr)?\\b|\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b(\\d{1,2}):(\\d{2})\\s*(?:uhr)?\\b",
			FLAGS);

	static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

	private WhenParser() {
	}

	/**
	 * @return the moment and the text spans it was read from (so they can be cut from a title), or
	 * empty if the text names no day
	 */
	static Optional<When> parse(String text, LocalDate today) {
		List<int[]> spans = new ArrayList<>();
		LocalDate date = date(text, today, spans);
		if (date == null) {
			return Optional.empty();
		}
		LocalTime time = time(text, spans);
		return Optional.of(new When(LocalDateTime.of(date, (time != null) ? time : DEFAULT_TIME), spans));
	}

	private static LocalDate date(String text, LocalDate today, List<int[]> spans) {
		Matcher m = DAY_AFTER_TOMORROW.matcher(text);
		if (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			return today.plusDays(2);
		}
		m = IN_N.matcher(text);
		if (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			String n = m.group(1).toLowerCase(Locale.ROOT);
			int count = NUMBERS.containsKey(n) ? NUMBERS.get(n) : Integer.parseInt(n);
			boolean weeks = m.group(2).toLowerCase(Locale.ROOT).startsWith("w");
			return weeks ? today.plusWeeks(count) : today.plusDays(count);
		}
		m = NUMERIC_DATE.matcher(text);
		if (m.find()) {
			LocalDate d = safeDate(m.group(3), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)), today);
			if (d != null) {
				spans.add(new int[] { m.start(), m.end() });
				return d;
			}
		}
		m = DAY_MONTH.matcher(text);
		if (m.find()) {
			LocalDate d = safeDate(null, month(m.group(2)), Integer.parseInt(m.group(1)), today);
			if (d != null) {
				spans.add(new int[] { m.start(), m.end() });
				return d;
			}
		}
		m = MONTH_DAY.matcher(text);
		if (m.find()) {
			LocalDate d = safeDate(null, month(m.group(1)), Integer.parseInt(m.group(2)), today);
			if (d != null) {
				spans.add(new int[] { m.start(), m.end() });
				return d;
			}
		}
		m = NEXT_WEEK.matcher(text);
		if (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		}
		m = WEEKDAY.matcher(text);
		if (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			return today.with(TemporalAdjusters.next(WEEKDAYS.get(m.group(1).toLowerCase(Locale.ROOT))));
		}
		m = RELATIVE_DAY.matcher(text);
		if (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			String word = m.group(1).toLowerCase(Locale.ROOT);
			return (word.equals("today") || word.equals("heute")) ? today : today.plusDays(1);
		}
		return null;
	}

	private static LocalTime time(String text, List<int[]> spans) {
		Matcher m = TIME.matcher(text);
		while (m.find()) {
			if (overlaps(spans, m.start(), m.end())) {
				continue;   // e.g. the "20.10." of a date
			}
			String hourText = firstNonNull(m.group(1), m.group(4), m.group(7));
			String minuteText = firstNonNull(m.group(2), m.group(5), m.group(8));
			String suffix = firstNonNull(m.group(3), m.group(6), null);
			int hour = Integer.parseInt(hourText);
			int minute = (minuteText != null) ? Integer.parseInt(minuteText) : 0;
			if (suffix != null && suffix.equalsIgnoreCase("pm") && hour < 12) {
				hour += 12;
			}
			if (suffix != null && suffix.equalsIgnoreCase("am") && hour == 12) {
				hour = 0;
			}
			if (hour > 23 || minute > 59) {
				continue;
			}
			spans.add(new int[] { m.start(), m.end() });
			return LocalTime.of(hour, minute);
		}
		return null;
	}

	/** Without a year, the next such date from today on. */
	private static LocalDate safeDate(String year, int month, int day, LocalDate today) {
		try {
			if (year != null) {
				int y = Integer.parseInt(year);
				return LocalDate.of((y < 100) ? 2000 + y : y, month, day);
			}
			LocalDate d = LocalDate.of(today.getYear(), month, day);
			return d.isBefore(today) ? d.plusYears(1) : d;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static int month(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		String[][] prefixes = { { "jan" }, { "feb" }, { "mar", "mär" }, { "apr" }, { "may", "mai" }, { "jun" },
				{ "jul" }, { "aug" }, { "sep" }, { "oct", "okt" }, { "nov" }, { "dec", "dez" } };
		for (int i = 0; i < prefixes.length; i++) {
			for (String p : prefixes[i]) {
				if (n.startsWith(p)) {
					return i + 1;
				}
			}
		}
		return 1;
	}

	private static boolean overlaps(List<int[]> spans, int start, int end) {
		return spans.stream().anyMatch((s) -> start < s[1] && end > s[0]);
	}

	private static String firstNonNull(String a, String b, String c) {
		return (a != null) ? a : (b != null) ? b : c;
	}

	/** @param spans character ranges [start, end) of the text that named the moment */
	record When(LocalDateTime at, List<int[]> spans) {

		/** The text with the date and time phrases cut out. */
		String without(String text) {
			StringBuilder sb = new StringBuilder(text);
			this.spans.stream()
				.sorted((a, b) -> Integer.compare(b[0], a[0]))
				.forEach((s) -> sb.replace(s[0], s[1], " "));
			return sb.toString();
		}

	}

}
