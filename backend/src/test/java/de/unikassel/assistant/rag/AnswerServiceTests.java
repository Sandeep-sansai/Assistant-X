package de.unikassel.assistant.rag;

import java.time.OffsetDateTime;
import java.util.List;

import de.unikassel.assistant.rag.AnswerService.Source;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerServiceTests {

	private static final List<Source> SOURCES = List.of(source(1), source(2), source(3), source(4));

	@Test
	void findsSingleAndGroupedCitations() {
		String answer = "Register in eCampus [1, 3]. The office helps too [4]. Not a citation: [x], [ 2 ].";

		assertThat(AnswerService.cited(answer, SOURCES)).extracting(Source::number).containsExactly(1, 3, 4);
	}

	@Test
	void ignoresNumbersWithoutASource() {
		assertThat(AnswerService.cited("See [7] and [2].", SOURCES)).extracting(Source::number).containsExactly(2);
	}

	private static Source source(int number) {
		return new Source(number, "Page " + number, "https://example.org/" + number, OffsetDateTime.now());
	}

}
