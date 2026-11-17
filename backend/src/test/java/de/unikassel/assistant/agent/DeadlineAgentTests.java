package de.unikassel.assistant.agent;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineAgentTests {

	private final DeadlineAgent agent = new DeadlineAgent(null);

	@Test
	void turnsAnEnglishRequestIntoAReminder() {
		AgentReply reply = this.agent.handle(ConciergeTests.request("Remind me to pay the semester fee on Friday at 10", "en"));

		assertThat(reply.actions()).extracting(AgentReply.Action::type).containsExactly("REMINDER", "CALENDAR");
		AgentReply.Action save = reply.actions().getFirst();
		assertThat(save.title()).isEqualTo("Pay the semester fee");
		assertThat(save.at()).isEqualTo(LocalDateTime.of(2026, 10, 16, 10, 0));
		assertThat(reply.text()).contains("pay the semester fee", "Friday 16 October at 10:00");
	}

	@Test
	void turnsAGermanRequestIntoAReminder() {
		AgentReply reply = this.agent.handle(
				ConciergeTests.request("Erinnere mich am Freitag daran, den Semesterbeitrag zu überweisen", "de"));

		AgentReply.Action save = reply.actions().getFirst();
		assertThat(save.title()).isEqualTo("Den Semesterbeitrag zu überweisen");
		assertThat(save.at()).isEqualTo(LocalDateTime.of(2026, 10, 16, 9, 0));
		assertThat(reply.text()).isEqualTo(
				"Soll ich dich am Freitag, 16. Oktober um 09:00 Uhr daran erinnern, **den Semesterbeitrag zu überweisen**?");
	}

	@Test
	void phrasesGermanRemindersNaturally() {
		assertThat(DeadlineAgent.germanQuestion("Die CampusCard abzuholen", "Freitag"))
			.isEqualTo("Soll ich dich am Freitag daran erinnern, **die CampusCard abzuholen**?");
		assertThat(DeadlineAgent.germanQuestion("Die Bibliothek", "Freitag"))
			.isEqualTo("Soll ich dich am Freitag an **die Bibliothek** erinnern?");
		assertThat(DeadlineAgent.germanQuestion("CampusCard abholen", "Freitag"))
			.isEqualTo("Soll ich dich am Freitag an **CampusCard abholen** erinnern?");
	}

	@Test
	void asksForADayWhenThereIsNone() {
		AgentReply reply = this.agent.handle(ConciergeTests.request("remind me to call the Prüfungsamt", "en"));

		assertThat(reply.actions()).isEmpty();
		assertThat(reply.text()).startsWith("When should I remind you?");
	}

	@Test
	void refusesTimesInThePast() {
		// "Now" in the test request is 14 October, 8:00.
		AgentReply reply = this.agent.handle(ConciergeTests.request("remind me today at 7 to get up", "en"));

		assertThat(reply.actions()).isEmpty();
		assertThat(reply.text()).contains("already passed");
	}

}
