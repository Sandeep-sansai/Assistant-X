package de.unikassel.assistant.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import de.unikassel.assistant.i18n.Translator;
import de.unikassel.assistant.planner.PlanRequest;
import de.unikassel.assistant.planner.StudyPlan;
import de.unikassel.assistant.planner.StudyPlanner;
import de.unikassel.assistant.programme.ModuleHistory;
import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.ProgrammeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The study planner: programmes from the regulations, generated plans and checks of edited plans. */
@RestController
@RequestMapping("/api")
class PlannerController {

	private final ProgrammeService programmes;

	private final Translator translator;

	PlannerController(ProgrammeService programmes, Translator translator) {
		this.programmes = programmes;
		this.translator = translator;
	}

	@GetMapping("/programmes")
	List<ProgrammeInfo> programmes() {
		return this.programmes.sources().stream().map((s) -> {
			Programme p = this.programmes.programme(s.id()).orElse(null);
			List<FocusInfo> focus = (p == null) ? List.of()
					: p.focusOptions().stream().map((f) -> new FocusInfo(f.code(), f.title(),
							this.translator.english(f.title()).orElse(null), f.credits())).toList();
			return new ProgrammeInfo(s.id(), s.title(), s.department(), s.names(), p != null,
					(p != null) ? p.regularSemesters() : null, (p != null) ? p.totalCredits() : null, focus, s.url(),
					(p != null) ? p.fetchedAt() : null);
		}).toList();
	}

	@GetMapping("/programmes/{id}")
	ProgrammeDetail programme(@PathVariable String id) {
		Programme p = find(id);
		Map<String, HistoryInfo> history = new HashMap<>();
		for (ModuleHistory h : this.programmes.history().values()) {
			if (p.modules().containsKey(h.code())) {
				history.put(h.code(), new HistoryInfo(h.offered().name(), h.passRate(),
						h.terms().stream().filter(ModuleHistory.Term::offered).map(ModuleHistory.Term::semester).toList()));
			}
		}
		List<String> titles = new java.util.ArrayList<>();
		p.groups().forEach((g) -> titles.add(g.title()));
		p.modules().values().forEach((m) -> titles.add(m.title()));
		return new ProgrammeDetail(p, history, this.translator.english(titles));
	}

	@PostMapping("/planner/plan")
	StudyPlan plan(@Valid @RequestBody PlanBody body) {
		Programme p = find(body.programme());
		requireFocus(p, body.focus());
		return new StudyPlanner(p, this.programmes.history()).plan(new PlanRequest(body.focus(), body.start(),
				body.semesters(), body.completed(), body.preferred(), body.maxCreditsPerSemester()));
	}

	@PostMapping("/planner/check")
	StudyPlan check(@Valid @RequestBody CheckBody body) {
		Programme p = find(body.programme());
		requireFocus(p, body.focus());
		return new StudyPlanner(p, this.programmes.history()).evaluate(body.focus(), body.start(), body.semesters(),
				new HashSet<>((body.completed() != null) ? body.completed() : List.of()), body.maxCreditsPerSemester());
	}

	private Programme find(String id) {
		return this.programmes.programme(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme not imported yet"));
	}

	private static void requireFocus(Programme p, String focus) {
		if (p.group(focus) == null || p.focusOptions().stream().noneMatch((f) -> f.code().equals(focus))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown focus");
		}
	}

	record ProgrammeInfo(String id, String title, String department, List<String> names, boolean imported,
			Integer regularSemesters, Integer totalCredits, List<FocusInfo> focusOptions, String sourceUrl,
			Instant fetchedAt) {
	}

	/** @param titleEn English name, or null until it is translated */
	record FocusInfo(String code, String title, String titleEn, Integer credits) {
	}

	/**
	 * @param history past offerings per module code, once the university's data is configured
	 * @param english English versions of the German module and area names (original → English)
	 */
	record ProgrammeDetail(Programme programme, Map<String, HistoryInfo> history, Map<String, String> english) {
	}

	record HistoryInfo(String offered, Double passRate, List<String> offeredIn) {
	}

	record PlanBody(@NotBlank String programme, @NotBlank String focus,
			@NotBlank @Pattern(regexp = "(WS|SS)\\d{4}") String start, @Min(1) @Max(12) int semesters,
			@Size(max = 100) List<String> completed, @Size(max = 100) List<String> preferred,
			@Min(6) @Max(60) Integer maxCreditsPerSemester) {
	}

	record CheckBody(@NotBlank String programme, @NotBlank String focus,
			@NotBlank @Pattern(regexp = "(WS|SS)\\d{4}") String start, @Valid @Size(min = 1, max = 12) List<List<String>> semesters,
			@Size(max = 100) List<String> completed, @Min(6) @Max(60) Integer maxCreditsPerSemester) {
	}

}
