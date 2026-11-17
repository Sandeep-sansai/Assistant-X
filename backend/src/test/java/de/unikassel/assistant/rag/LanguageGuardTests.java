package de.unikassel.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageGuardTests {

	@Test
	void recognisesGermanAnswers() {
		String answer = "Die genauen Anweisungen für die Anmeldung zur Prüfung können Sie in der Prüfungsordnung finden, "
				+ "aber in den Quellen sind keine Informationen dazu enthalten [1].";

		assertThat(LanguageGuard.detect(answer)).isEqualTo("de");
	}

	@Test
	void recognisesEnglishAnswersEvenWithGermanTerms() {
		String answer = "You have to complete the Rückmeldung (re-registration) by paying the semester contribution. "
				+ "If you miss the deadline, there is a late fee of 15 euros [2].";

		assertThat(LanguageGuard.detect(answer)).isEqualTo("en");
	}

	@Test
	void doesNotGuessOnShortText() {
		assertThat(LanguageGuard.detect("Rückmeldung [1]")).isNull();
	}

	@Test
	void namesOnlySupportedLanguages() {
		assertThat(LanguageGuard.name("en")).isEqualTo("English");
		assertThat(LanguageGuard.name("de")).isEqualTo("German");
		assertThat(LanguageGuard.name("fr")).isNull();
		assertThat(LanguageGuard.name(null)).isNull();
	}

}
