package de.unikassel.assistant.web;

import java.util.List;

import de.unikassel.assistant.config.UniversityProperties;
import de.unikassel.assistant.config.UniversityProperties.Department;
import de.unikassel.assistant.rag.AnswerService;
import de.unikassel.assistant.rag.AnswerService.Answer;
import de.unikassel.assistant.rag.QuestionLog;
import de.unikassel.assistant.rag.StudentContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AskController {

	private final AnswerService answers;

	private final QuestionLog questionLog;

	private final List<Department> departments;

	AskController(AnswerService answers, QuestionLog questionLog, UniversityProperties university) {
		this.answers = answers;
		this.questionLog = questionLog;
		this.departments = university.departments();
	}

	@PostMapping("/ask")
	Answer ask(@Valid @RequestBody AskRequest request) {
		return this.answers.answer(request.question().strip(), studentContext(request.profile()), request.language());
	}

	/** Renders the profile the student picked in the browser; nothing about it is stored. */
	private String studentContext(Profile profile) {
		if (profile == null) {
			return null;
		}
		String departmentName = this.departments.stream()
			.filter((d) -> d.id().equals(profile.department()))
			.map(Department::name)
			.findFirst()
			.orElse(null);
		return StudentContext.render(departmentName, profile.programme(), profile.semester());
	}

	/** @param language "en" or "de": the language the student chose on the page; answers use it */
	record AskRequest(@NotBlank @Size(max = 1000) String question, @Valid Profile profile,
			@Pattern(regexp = "en|de") String language) {
	}

	record Profile(@Size(max = 30) String department, @Size(max = 100) String programme,
			@Min(1) @Max(20) Integer semester) {
	}

	record FeedbackRequest(@Min(-1) @Max(1) int rating, @Size(max = 2000) String comment) {
	}

}
