package de.unikassel.assistant.agent;

import java.time.LocalDate;

import de.unikassel.assistant.timetable.TimetableService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableAgentTests {

	private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 20);

	@Test
	void findsTheDayTheQuestionIsAbout() {
		assertThat(TimetableAgent.day("what classes do I have today?", TUESDAY)).isEqualTo(TUESDAY);
		assertThat(TimetableAgent.day("lectures tomorrow", TUESDAY)).isEqualTo(TUESDAY.plusDays(1));
		assertThat(TimetableAgent.day("what are the classes on monday", TUESDAY)).isEqualTo(LocalDate.of(2026, 10, 26));
		assertThat(TimetableAgent.day("Welche Vorlesungen habe ich am Donnerstag?", TUESDAY)).isEqualTo(LocalDate.of(2026, 10, 22));
	}

	@Test
	void showsCatalogueLabelsInEnglish() {
		assertThat(TimetableService.kindLabel("Vorlesung/Übung", true)).isEqualTo("Lecture/exercise");
		assertThat(TimetableService.kindLabel("Übung · 2. PG", true)).isEqualTo("Exercise · group 2");
		assertThat(TimetableService.kindLabel("Übung", false)).isEqualTo("Übung");
		assertThat(TimetableService.roomLabel("Hörsaal 0425 (WA-altes Gebäude (WA 73) (EG))", true))
			.isEqualTo("Lecture hall 0425 (WA old building (WA 73) (ground floor))");
		assertThat(TimetableService.roomLabel("Raum 1409 (Heinr.-Plett-Str. (1.OG))", true)).isEqualTo("Room 1409 (Heinr.-Plett-Str. (floor 1))");
	}

}
