package de.unikassel.assistant.ingest;

import java.util.List;

import de.unikassel.assistant.ingest.Chunker.ChunkDraft;
import de.unikassel.assistant.ingest.ContentExtractor.Section;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTests {

	private final Chunker chunker = new Chunker();

	@Test
	void packsShortParagraphsTogether() {
		List<ChunkDraft> chunks = this.chunker.chunk(List.of(new Section("A", List.of("one", "two", "three"))));

		assertThat(chunks).containsExactly(new ChunkDraft("A", "one\ntwo\nthree"));
	}

	@Test
	void neverMixesSections() {
		List<ChunkDraft> chunks = this.chunker
			.chunk(List.of(new Section("A", List.of("one")), new Section("A > B", List.of("two"))));

		assertThat(chunks).extracting(ChunkDraft::heading).containsExactly("A", "A > B");
	}

	@Test
	void splitsLongTextAndKeepsEveryChunkUnderTheLimit() {
		String sentence = "Dies ist ein Satz aus einer Prüfungsordnung mit einigen Wörtern. ";
		String longParagraph = sentence.repeat(100);
		String noSentenceEnds = "x".repeat(5000);

		List<ChunkDraft> chunks = this.chunker
			.chunk(List.of(new Section("PO", List.of(longParagraph, noSentenceEnds))));

		assertThat(chunks).hasSizeGreaterThan(5)
			.allSatisfy((c) -> assertThat(c.text().length()).isLessThanOrEqualTo(Chunker.MAX_CHARS));
		int totalChars = chunks.stream().mapToInt((c) -> c.text().replaceAll("\\s", "").length()).sum();
		assertThat(totalChars).isEqualTo((longParagraph + noSentenceEnds).replaceAll("\\s", "").length());
	}

}
