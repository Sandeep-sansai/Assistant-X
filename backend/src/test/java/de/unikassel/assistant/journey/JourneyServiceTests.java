package de.unikassel.assistant.journey;

import java.time.LocalDate;
import java.util.List;

import de.unikassel.assistant.journey.JourneyProperties.Journey;
import de.unikassel.assistant.journey.JourneyProperties.Step;
import de.unikassel.assistant.journey.JourneyProperties.StepTexts;
import de.unikassel.assistant.journey.JourneyProperties.Texts;
import de.unikassel.assistant.journey.JourneyService.ActiveJourney;
import de.unikassel.assistant.journey.JourneyService.ActiveStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyServiceTests {

	private static final LocalDate OCT_14 = LocalDate.of(2026, 10, 14);

	private final JourneyService service = new JourneyService(new JourneyProperties(List.of(new Journey("first-weeks",
			1, 1, 45, 42, "Your first weeks", "{n} things to do", new Texts("Deine ersten Wochen", null),
			List.of(step("uniaccount", null, null), step("intro", null, null), step("intro-fb16", "fb16", "intro"))))));

	@Test
	void offersTheJourneyInTheFirstSemesterOnly() {
		assertThat(this.service.active("fb07", "WS2026", OCT_14, "en")).extracting(ActiveJourney::id)
			.containsExactly("first-weeks");
		assertThat(this.service.active("fb07", "SS2026", OCT_14, "en")).isEmpty();
		assertThat(this.service.active("fb07", null, OCT_14, "en")).isEmpty();
		assertThat(this.service.active("fb07", "winter", OCT_14, "en")).isEmpty();
	}

	@Test
	void offersTheJourneyFromBeforeTheStartUntilSomeWeeksIn() {
		// 45 days before 1 October is 17 August; 42 days after is 12 November.
		assertThat(this.service.active("fb07", "WS2026", LocalDate.of(2026, 8, 16), "en")).isEmpty();
		assertThat(this.service.active("fb07", "WS2026", LocalDate.of(2026, 8, 17), "en")).hasSize(1);
		assertThat(this.service.active("fb07", "WS2026", LocalDate.of(2026, 11, 12), "en")).hasSize(1);
		assertThat(this.service.active("fb07", "WS2026", LocalDate.of(2026, 11, 13), "en")).isEmpty();
	}

	@Test
	void countsSemestersFromTheStudyStart() {
		LocalDate ws2026 = LocalDate.of(2026, 10, 1);
		assertThat(JourneyService.semesterIn(ws2026, JourneyService.parseTerm("WS2026"))).isEqualTo(1);
		assertThat(JourneyService.semesterIn(ws2026, JourneyService.parseTerm("SS2026"))).isEqualTo(2);
		assertThat(JourneyService.semesterIn(ws2026, JourneyService.parseTerm("WS2024"))).isEqualTo(5);
	}

	@Test
	void replacesGeneralStepsWithDepartmentSpecificOnes() {
		assertThat(this.service.active("fb16", "WS2026", OCT_14, "en").getFirst().steps()).extracting(ActiveStep::id)
			.containsExactly("uniaccount", "intro-fb16");
		assertThat(this.service.active("fb07", "WS2026", OCT_14, "en").getFirst().steps()).extracting(ActiveStep::id)
			.containsExactly("uniaccount", "intro");
	}

	@Test
	void fillsInTheStepCountAndTranslates() {
		ActiveJourney en = this.service.active("fb07", "WS2026", OCT_14, "en").getFirst();
		assertThat(en.prompt()).isEqualTo("2 things to do");
		ActiveJourney de = this.service.active("fb07", "WS2026", OCT_14, "de").getFirst();
		assertThat(de.title()).isEqualTo("Deine ersten Wochen");
		assertThat(de.prompt()).isEqualTo("2 things to do");
		assertThat(de.steps().getFirst().title()).isEqualTo("uniaccount (de)");
	}

	@Test
	void knowsWhenEachSemesterStarts() {
		assertThat(JourneyService.termStart(LocalDate.of(2026, 10, 14))).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(JourneyService.termStart(LocalDate.of(2027, 2, 3))).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(JourneyService.termStart(LocalDate.of(2027, 5, 20))).isEqualTo(LocalDate.of(2027, 4, 1));
	}

	private static Step step(String id, String department, String replaces) {
		return new Step(id, department, replaces, id, "detail", "ask " + id, "https://example.org", null, null, null,
				new StepTexts(id + " (de)", null, null, null));
	}

}
