package de.unikassel.assistant.i18n;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslatorTests {

	@Test
	void readsNumberedLinesOfTheModelReply() {
		Map<Integer, String> lines = Translator.parse("""
				Here you go:
				1. Fundamentals of Energy Engineering
				2) "Functional Programming"
				3. Pattern Recognition and Machine Learning I
				""");

		assertThat(lines).containsEntry(1, "Fundamentals of Energy Engineering")
			.containsEntry(2, "Functional Programming")
			.containsEntry(3, "Pattern Recognition and Machine Learning I")
			.hasSize(3);
	}

	@Test
	void rejectsAnswersThatAreNotATitle() {
		assertThat(Translator.plausible("Numerik I", "Numerical Analysis I")).isTrue();
		assertThat(Translator.plausible("Numerik I", "")).isFalse();
		assertThat(Translator.plausible("Numerik I",
				"This is a German module title that translates to numerical analysis, which covers methods for")).isFalse();
	}

	@Test
	void keepsTitlesThatWereAlreadyEnglish() {
		assertThat(Translator.tidy("Agent-Based Modelling Lab", "Agent-Based Modeling Lab")).isEqualTo("Agent-Based Modelling Lab");
		assertThat(Translator.tidy("Internet and Systems Security", "Internet and System Security"))
			.isEqualTo("Internet and Systems Security");
		assertThat(Translator.tidy("Writing your application I: CV (English)", "Writing your application I: CV"))
			.isEqualTo("Writing your application I: CV (English)");
		assertThat(Translator.tidy("Collaboration Engineering", "Collaboration engineering")).isEqualTo("Collaboration Engineering");
		assertThat(Translator.tidy("Funktionale Programmierung", "Functional Programming")).isEqualTo("Functional Programming");
		assertThat(Translator.tidy("Projekt", "Project")).isEqualTo("Project");
		assertThat(Translator.tidy("Numerik I", "Numerical Analysis I")).isEqualTo("Numerical Analysis I");
		assertThat(Translator.tidy("Ökologie und Globale Stoffkreisläufe", "Ecology and Global Material Cycles"))
			.isEqualTo("Ecology and Global Material Cycles");
	}

	@Test
	void translatesGermanAroundAnEnglishQuote() {
		assertThat(Translator.tidy("Vortrag von Lars Bittcher zu \"Hybrid, data-driven approaches to dynamic black-box model fitting\"",
				"Talk by Lars Bittcher on \"Hybrid, data-driven approaches to dynamic black-box model fitting\""))
			.startsWith("Talk by Lars Bittcher");
	}

	@Test
	void namesTalkAnnouncementsWithoutTheModel() {
		assertThat(Translator.fixed("Vortrag von Christian Nauck zu \"Dynamic Stability Assessment\""))
			.isEqualTo("Talk by Christian Nauck: \"Dynamic Stability Assessment\"");
		assertThat(Translator.fixed("Akademische Semesterfeier")).isNull();
		assertThat(Translator.fixed("Labor Grand Challenges of Machine Learning")).isEqualTo("Grand Challenges of Machine Learning Lab");
		assertThat(Translator.fixed("Labor Netzwerke")).isNull();
	}

	@Test
	void restoresADroppedClosingQuote() {
		assertThat(Translator.tidy("Vortrag von A. B. zu \"Graph Neural Networks\"", "Talk by A. B. on \"Graph Neural Networks"))
			.isEqualTo("Talk by A. B. on \"Graph Neural Networks\"");
	}

	@Test
	void namesTheAreasOfTheRegulationsFromTheGlossaryWithoutTheModel() {
		Translator translator = new Translator(null, null);

		assertThat(translator.english("Wahlpflicht Praktische Informatik")).contains("Applied Computer Science");
		assertThat(translator.english("Schwerpunkt Umweltinformatik (UW)")).contains("Environmental Informatics (UW)");
		assertThat(translator.english("Masterabschlussmodul")).contains("Master's thesis");
	}

}
