package de.unikassel.assistant.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import de.unikassel.assistant.agent.AgentReply;
import de.unikassel.assistant.agent.AgentRequest;
import de.unikassel.assistant.agent.AgentRequest.Reminder;
import de.unikassel.assistant.agent.Concierge;
import de.unikassel.assistant.config.UniversityProperties;
import de.unikassel.assistant.config.UniversityProperties.Department;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The chat: every message goes to the concierge, which hands it to the right agent. */
@RestController
@RequestMapping("/api")
class AssistantController {

	private final Concierge concierge;

	private final UniversityProperties university;

	AssistantController(Concierge concierge, UniversityProperties university) {
		this.concierge = concierge;
		this.university = university;
	}

	@PostMapping("/assistant")
	AgentReply message(@Valid @RequestBody Message m) {
		Profile p = (m.profile() != null) ? m.profile() : new Profile(null, null, null, null);
		String departmentName = this.university.departments()
			.stream()
			.filter((d) -> d.id().equals(p.department()))
			.map(Department::name)
			.findFirst()
			.orElse(null);
		AgentRequest request = new AgentRequest(m.message().strip(), m.language(),
				(departmentName != null) ? p.department() : null, departmentName, p.programme(), p.semester(), p.start(),
				m.journeyDone(), m.reminders(), LocalDateTime.now(this.university.zone()), m.planFocus(),
				m.completedModules(), m.plannedModules(),
				(m.history() != null) ? m.history().stream().map((h) -> new AgentRequest.Turn(h.question(), h.answer())).toList() : null);
		return this.concierge.handle(request);
	}

	/**
	 * @param journeyDone finished journey steps, kept in the browser until there are accounts
	 * @param reminders the student's reminders, kept in the browser until there are accounts
	 * @param planFocus focus chosen in the study planner
	 * @param completedModules modules marked as passed in the study planner
	 */
	record Message(@NotBlank @Size(max = 1000) String message, @Valid Profile profile,
			@Pattern(regexp = "en|de") String language, Map<String, List<String>> journeyDone,
			@Size(max = 200) List<Reminder> reminders, @Size(max = 20) String planFocus,
			@Size(max = 100) List<String> completedModules, @Size(max = 40) List<String> plannedModules,
			@Valid @Size(max = 6) List<HistoryTurn> history) {
	}

	/** The browser keeps the chat; answers are cut to keep the request small. */
	record HistoryTurn(@Size(max = 1000) String question, @Size(max = 4000) String answer) {
	}

	record Profile(@Size(max = 30) String department, @Size(max = 100) String programme,
			@Min(1) @Max(20) Integer semester, @Pattern(regexp = "(WS|SS)\\d{4}") String start) {
	}

}
