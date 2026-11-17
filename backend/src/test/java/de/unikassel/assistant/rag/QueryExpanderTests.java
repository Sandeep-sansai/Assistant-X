package de.unikassel.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExpanderTests {

	@Test
	void addsTheGermanTermsTheUniversityPagesUse() {
		assertThat(QueryExpander.germanTerms("How much is the semester contribution?")).contains("Semesterbeitrag");
		assertThat(QueryExpander.germanTerms("I missed the re-registration deadline")).contains("Rückmeldung", "Frist");
		assertThat(QueryExpander.germanTerms("How do I register for an exam?")).contains("Prüfungsanmeldung");
		assertThat(QueryExpander.germanTerms("Where is the exam office?")).contains("Prüfungsamt");
	}

	@Test
	void addsNothingForUnknownTopics() {
		assertThat(QueryExpander.germanTerms("Tell me a joke")).isEmpty();
	}

}
