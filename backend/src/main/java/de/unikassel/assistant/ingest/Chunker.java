package de.unikassel.assistant.ingest;

import java.util.ArrayList;
import java.util.List;

import de.unikassel.assistant.ingest.ContentExtractor.Section;

import org.springframework.stereotype.Component;

/**
 * Packs paragraphs into chunks of roughly {@link #TARGET_CHARS} characters. A chunk never spans
 * two sections, so its heading path always describes all of its text.
 */
@Component
class Chunker {

	static final int TARGET_CHARS = 1200;

	static final int MAX_CHARS = 2000;

	List<ChunkDraft> chunk(List<Section> sections) {
		List<ChunkDraft> chunks = new ArrayList<>();
		for (Section section : sections) {
			StringBuilder current = new StringBuilder();
			for (String para : section.paragraphs()) {
				for (String piece : splitLong(para)) {
					if (!current.isEmpty() && current.length() + piece.length() > TARGET_CHARS) {
						chunks.add(new ChunkDraft(section.heading(), current.toString()));
						current.setLength(0);
					}
					if (!current.isEmpty()) {
						current.append('\n');
					}
					current.append(piece);
				}
			}
			if (!current.isEmpty()) {
				chunks.add(new ChunkDraft(section.heading(), current.toString()));
			}
		}
		return chunks;
	}

	/** Splits a paragraph longer than {@link #MAX_CHARS} at sentence ends, or hard if it has none. */
	private static List<String> splitLong(String para) {
		if (para.length() <= MAX_CHARS) {
			return List.of(para);
		}
		List<String> pieces = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String sentence : para.split("(?<=[.!?;])\\s+")) {
			if (!current.isEmpty() && current.length() + sentence.length() > TARGET_CHARS) {
				pieces.add(current.toString());
				current.setLength(0);
			}
			while (sentence.length() > MAX_CHARS) {
				pieces.add(sentence.substring(0, MAX_CHARS));
				sentence = sentence.substring(MAX_CHARS);
			}
			if (!current.isEmpty()) {
				current.append(' ');
			}
			current.append(sentence);
		}
		if (!current.isEmpty()) {
			pieces.add(current.toString());
		}
		return pieces;
	}

	record ChunkDraft(String heading, String text) {
	}

}
