package de.unikassel.assistant.rag;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells English from German by counting common function words. Enough to notice that a small
 * model answered in the language of the sources instead of the one the student chose.
 */
final class LanguageGuard {

	private static final Set<String> GERMAN = Set.of("der", "die", "das", "und", "ist", "nicht", "sie", "sich", "mit",
			"für", "von", "werden", "wird", "auf", "eine", "einen", "den", "dem", "des", "zu", "im", "bei", "oder", "auch",
			"können", "kann", "ihre", "ihr", "wenn", "dass", "nach", "über", "ich", "du", "dein", "deine", "sind", "zum",
			"zur");

	private static final Set<String> ENGLISH = Set.of("the", "and", "is", "are", "not", "you", "your", "with", "for",
			"from", "will", "can", "this", "that", "to", "of", "on", "or", "if", "be", "have", "has", "it", "at", "by",
			"after", "about", "there", "which", "should", "must");

	private static final Pattern WORD = Pattern.compile("\\p{L}+");

	/** Below this many function words a guess is not worth acting on. */
	private static final int MIN_EVIDENCE = 6;

	private LanguageGuard() {
	}

	/** Returns "en", "de", or null when the text is too short or too mixed to tell. */
	static String detect(String text) {
		int german = 0;
		int english = 0;
		Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
		while (m.find()) {
			String word = m.group();
			if (GERMAN.contains(word)) {
				german++;
			}
			if (ENGLISH.contains(word)) {
				english++;
			}
		}
		if (german + english < MIN_EVIDENCE) {
			return null;
		}
		if (german > english * 2) {
			return "de";
		}
		if (english > german * 2) {
			return "en";
		}
		return null;
	}

	/** The language's English name for prompts, or null for anything but "en" and "de". */
	static String name(String language) {
		if ("en".equals(language)) {
			return "English";
		}
		if ("de".equals(language)) {
			return "German";
		}
		return null;
	}

}
