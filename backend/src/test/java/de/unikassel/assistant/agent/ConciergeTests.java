package de.unikassel.assistant.agent;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConciergeTests {

	private final RegulationsAgent regulations = new RegulationsAgent(null);

	private final Concierge concierge = new Concierge(List.of(new DeadlineAgent(null), new OnboardingAgent(null),
			new CampusAgent(null), new PlannerAgent(null, null), this.regulations), this.regulations);

	private String routeOf(String message) {
		return this.concierge.route(request(message, "en")).id();
	}

	@Test
	void sendsRemindersAndOverviewsToTheDeadlineAgent() {
		assertThat(routeOf("Remind me to pay the semester fee on Friday")).isEqualTo("deadlines");
		assertThat(routeOf("Erinnere mich morgen an die Bibliothek")).isEqualTo("deadlines");
		assertThat(routeOf("What's coming up?")).isEqualTo("deadlines");
		assertThat(routeOf("Was steht als Nächstes an?")).isEqualTo("deadlines");
		// A reminder about events is still a reminder.
		assertThat(routeOf("remind me about the events this week")).isEqualTo("deadlines");
	}

	@Test
	void sendsChecklistQuestionsToTheOnboardingCoach() {
		assertThat(routeOf("What's next on my checklist?")).isEqualTo("onboarding");
		assertThat(routeOf("Was muss ich als Erstes tun?")).isEqualTo("onboarding");
	}

	@Test
	void sendsWhatsOnQuestionsToTheCampusAgent() {
		assertThat(routeOf("What's on this week?")).isEqualTo("campus");
		assertThat(routeOf("Are there any events tomorrow?")).isEqualTo("campus");
		assertThat(routeOf("Was ist am Wochenende los?")).isEqualTo("campus");
	}

	@Test
	void sendsPlanningRequestsToThePlanner() {
		assertThat(routeOf("Plan my master in 5 semesters with the focus CIDA")).isEqualTo("planner");
		assertThat(routeOf("Can you make me a study plan?")).isEqualTo("planner");
		assertThat(routeOf("Plane mein Studium in sechs Semestern")).isEqualTo("planner");
		assertThat(PlannerAgent.semesters("in five semesters")).contains(5);
		assertThat(PlannerAgent.semesters("in sechs Semestern")).contains(6);
	}

	@Test
	void sendsEverythingElseToTheRegulationsAgent() {
		assertThat(routeOf("When do I have to re-register (Rückmeldung)?")).isEqualTo("regulations");
		// Mentions events, but asks about the regulations, not what's on.
		assertThat(routeOf("When are the introductory events for my programme?")).isEqualTo("regulations");
		assertThat(routeOf("How many ECTS is the master's thesis?")).isEqualTo("regulations");
	}

	@Test
	void answersHelpWithExamples() {
		AgentReply reply = this.concierge.handle(request("What can you do?", "de"));
		assertThat(reply.agent()).isEqualTo("concierge");
		assertThat(reply.actions()).hasSize(4).allMatch((a) -> a.type().equals("ASK"));
	}

	static AgentRequest request(String message, String language) {
		return new AgentRequest(message, language, "fb16", "FB 16 Elektrotechnik/Informatik", null, 1, "WS2026", null,
				null, LocalDateTime.of(2026, 10, 14, 8, 0));
	}

}
