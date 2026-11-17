package de.unikassel.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSearchTests {

	@Test
	void searchesForTheTopicNotTheQuestionWords() {
		assertThat(ChunkSearch.orQuery("How much is the semester contribution and what does it include? Semesterbeitrag"))
			.isEqualTo("semester or contribution or semesterbeitrag");
		assertThat(ChunkSearch.orQuery("Wie hoch ist der Semesterbeitrag?")).isEqualTo("hoch or semesterbeitrag");
	}

}
