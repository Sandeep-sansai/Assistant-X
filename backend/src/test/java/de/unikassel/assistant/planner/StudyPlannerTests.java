package de.unikassel.assistant.planner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.unikassel.assistant.programme.ModuleHistory;
import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.Programme.Group;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.Programme.Offered;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPlannerTests {

	/** Shaped like the Computer Science M.Sc. 2026: five 6-credit areas, project, seminar, a 48-credit focus, thesis. */
	static Programme programme() {
		Map<String, Module> modules = new LinkedHashMap<>();
		List<Group> groups = new ArrayList<>();
		groups.add(group("1", 6, modules, mod("PI-01", 6, Offered.WINTER, true), mod("PI-02", 6, Offered.SUMMER, false)));
		groups.add(group("2", 6, modules, mod("TI-01", 6, Offered.SUMMER, false), mod("TI-02", 6, Offered.WINTER, false)));
		groups.add(group("3", 6, modules, mod("TH-01", 6, Offered.WINTER, false)));
		groups.add(group("4", 6, modules, mod("ME-01", 6, Offered.SUMMER, false), mod("ME-02", 6, Offered.BOTH, false)));
		groups.add(new Group("5", "Free elective", 6, List.of("PI-01", "PI-02", "TI-01", "TI-02", "TH-01", "ME-01", "ME-02")));
		groups.add(group("6", 8, modules, mod("PROJ", 8, Offered.BOTH, false)));
		groups.add(group("7", 4, modules, mod("SEM", 4, Offered.BOTH, false)));
		groups.add(new Group("8.1", "Focus CIDA", 48, List.of()));
		groups.add(group("8.1.1", 18, modules, mod("C-01", 6, Offered.WINTER, false), mod("C-02", 6, Offered.SUMMER, false),
				mod("C-03", 6, Offered.WINTER, false), mod("C-04", 6, Offered.BOTH, false)));
		groups.add(group("8.1.2", 18, modules, mod("C-11", 6, Offered.SUMMER, false), mod("C-12", 6, Offered.WINTER, false),
				mod("C-13", 6, Offered.BOTH, false)));
		groups.add(group("8.1.3", 12, modules, mod("C-21", 6, Offered.WINTER, false), mod("C-22", 6, Offered.SUMMER, false),
				mod("C-23", 12, Offered.BOTH, false)));
		groups.add(new Group("8.6", "Free focus", 48, List.of("C-01", "C-02", "C-03", "C-04", "C-11", "C-12", "C-13",
				"C-21", "C-22", "C-23", "TI-02", "ME-02")));
		groups.add(group("9", 30, modules, mod("MA-CS", 30, Offered.BOTH, false)));
		return new Programme("cs-msc-2026", "Computer Science (M.Sc.)", 120, 4, 30, 60, 24, "https://example.org",
				Instant.EPOCH, groups, modules);
	}

	private static Group group(String code, int credits, Map<String, Module> modules, Module... members) {
		for (Module m : members) {
			modules.putIfAbsent(m.code(), m);
		}
		return new Group(code, "Area " + code, credits, java.util.Arrays.stream(members).map(Module::code).toList());
	}

	private static Module mod(String code, int credits, Offered offered, boolean oral) {
		return new Module(code, "Module " + code, credits, offered, offered.name(), "Englisch", 1, oral, "", "keine",
				"https://example.org/" + code);
	}

	private final StudyPlanner planner = new StudyPlanner(programme(), Map.of());

	@Test
	void plansAllOneHundredTwentyCreditsInFourSemesters() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.1", "WS2026", 4, List.of(), List.of(), null));

		assertThat(plan.credits()).isEqualTo(120);
		assertThat(plan.requirements()).allMatch(StudyPlan.Requirement::met);
		assertThat(plan.warnings()).isEmpty();
		assertThat(plan.semesters()).extracting(StudyPlan.Semester::term).containsExactly("WS2026", "SS2027", "WS2027", "SS2028");
		assertThat(plan.semesters()).extracting(StudyPlan.Semester::credits).containsExactly(30, 30, 30, 30);
	}

	@Test
	void respectsWhenModulesAreTaughtAndPutsTheThesisLast() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.1", "SS2027", 5, List.of(), List.of(), null));

		for (StudyPlan.Semester s : plan.semesters()) {
			for (StudyPlan.Planned m : s.modules()) {
				assertThat(Offered.valueOf(m.offered()).in(s.winter())).as(m.code() + " in " + s.term()).isTrue();
			}
		}
		StudyPlan.Semester last = plan.semesters().getLast();
		assertThat(last.modules()).extracting(StudyPlan.Planned::code).containsExactly("MA-CS");
		assertThat(plan.warnings()).isEmpty();
	}

	@Test
	void countsCompletedModulesAndPlansOnlyTheRest() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.1", "WS2026", 3, List.of("PI-01", "C-01", "C-02"), List.of(), null));

		Set<String> scheduled = plan.semesters().stream()
			.flatMap((s) -> s.modules().stream())
			.map(StudyPlan.Planned::code)
			.collect(Collectors.toSet());
		assertThat(scheduled).doesNotContain("PI-01", "C-01", "C-02");
		assertThat(plan.credits()).isEqualTo(120);
		assertThat(plan.requirements()).allMatch(StudyPlan.Requirement::met);
	}

	@Test
	void keepsTheOralExamRule() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.1", "WS2026", 4, List.of(), List.of(), null));

		assertThat(plan.semesters().stream().flatMap((s) -> s.modules().stream()).anyMatch(StudyPlan.Planned::oralExam)).isTrue();
	}

	@Test
	void prefersModulesTheStudentAskedFor() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.6", "WS2026", 4, List.of(), List.of("C-23"), null));

		assertThat(plan.semesters().stream().flatMap((s) -> s.modules().stream()).map(StudyPlan.Planned::code)).contains("C-23");
		assertThat(plan.requirements()).allMatch(StudyPlan.Requirement::met);
	}

	@Test
	void warnsAboutProblemsInAHandMadePlan() {
		// Summer-only TI-01 in a winter semester, the thesis in semester 1, and the focus left out.
		StudyPlan plan = this.planner.evaluate("8.1", "WS2026", List.of(List.of("MA-CS", "TI-01"), List.of("PI-02")), Set.of(), null);

		assertThat(plan.warnings()).extracting(StudyPlan.Warning::type)
			.contains("WRONG_TERM", "THESIS_TOO_EARLY", "REQUIREMENT_UNMET", "NO_ORAL_EXAM");
	}

	@Test
	void spreadsTheLoadWhenManyModulesAreTaughtInTheSameTerm() {
		// Summer-only modules dominate; a 4-semester plan starting in winter has one summer semester before the thesis.
		StudyPlan plan = this.planner.plan(new PlanRequest("8.6", "WS2026", 4, List.of(), List.of(), null));

		int max = plan.semesters().subList(0, 3).stream().mapToInt(StudyPlan.Semester::credits).max().orElse(0);
		assertThat(max).isLessThanOrEqualTo(StudyPlanner.HEAVY);
		assertThat(plan.credits()).isEqualTo(120);
	}

	@Test
	void flagsHeavySemestersWhenTimeIsShort() {
		StudyPlan plan = this.planner.plan(new PlanRequest("8.1", "WS2026", 3, List.of(), List.of(), null));

		assertThat(plan.warnings()).extracting(StudyPlan.Warning::type).contains("HEAVY_SEMESTER");
		assertThat(plan.credits()).isEqualTo(120);
	}

	@Test
	void usesPastDataToAvoidModulesThatAreNoLongerTaught() {
		ModuleHistory dropped = new ModuleHistory("C-04", List.of(
				new ModuleHistory.Term("WS 2024/25", true, false, null, null, null),
				new ModuleHistory.Term("SS 2025", false, false, null, null, null)));
		StudyPlanner withHistory = new StudyPlanner(programme(), Map.of("C-04", dropped));

		StudyPlan plan = withHistory.plan(new PlanRequest("8.1", "WS2026", 4, List.of(), List.of(), null));

		assertThat(plan.semesters().stream().flatMap((s) -> s.modules().stream()).map(StudyPlan.Planned::code))
			.doesNotContain("C-04");
	}

	@Test
	void namesTheTermsOfThePlan() {
		assertThat(StudyPlanner.termId("WS2026", 0)).isEqualTo("WS2026");
		assertThat(StudyPlanner.termId("WS2026", 1)).isEqualTo("SS2027");
		assertThat(StudyPlanner.termId("SS2027", 1)).isEqualTo("WS2027");
	}

}
