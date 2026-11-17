package de.unikassel.assistant.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.unikassel.assistant.programme.ModuleHistory;
import de.unikassel.assistant.programme.Programme;
import de.unikassel.assistant.programme.Programme.Group;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.Programme.Offered;

/**
 * Builds and checks study plans for a programme: which modules meet each requirement of the chosen
 * focus, and in which semester to take them so the student finishes in the time they want.
 * Deterministic, so the same choices always give the same plan.
 */
public final class StudyPlanner {

	/** The thesis module group in the regulations. */
	static final String THESIS_GROUP = "9";

	/** Above this a semester counts as heavy (a full-time semester is about 30 credits). */
	static final int HEAVY = 33;

	private static final Pattern TERM = Pattern.compile("(WS|SS)(\\d{4})");

	private final Programme programme;

	private final Map<String, ModuleHistory> history;

	public StudyPlanner(Programme programme, Map<String, ModuleHistory> history) {
		this.programme = programme;
		this.history = (history != null) ? history : Map.of();
	}

	// ---------------------------------------------------------------- requirements

	/** One credit requirement of the plan: a minimum number of credits from a set of modules. */
	record Need(String code, String title, int credits, List<String> modules) {
	}

	/** The requirements for a focus: all areas outside the focus options, plus the focus's own parts. */
	List<Need> needs(String focus) {
		List<Need> needs = new ArrayList<>();
		for (Group g : this.programme.groups()) {
			boolean topLevel = !g.code().contains(".");
			if (topLevel && g.credits() != null) {
				needs.add(new Need(g.code(), g.title(), g.credits(), g.modules()));
			}
		}
		Group f = this.programme.group(focus);
		if (f != null) {
			needs.addAll(leaves(f));
		}
		return needs;
	}

	/** A group's requirements: its own if its parts set no credits, otherwise those of its parts. */
	private List<Need> leaves(Group g) {
		List<Group> kids = this.programme.children(g.code());
		boolean kidsHaveCredits = kids.stream().anyMatch((k) -> k.credits() != null);
		if (kids.isEmpty() || !kidsHaveCredits) {
			return List.of(new Need(g.code(), g.title(), (g.credits() != null) ? g.credits() : 0, descendantModules(g)));
		}
		List<Need> needs = new ArrayList<>();
		int covered = 0;
		for (Group k : kids) {
			if (k.credits() != null) {
				needs.addAll(leaves(k));
				covered += k.credits();
			}
		}
		if (g.credits() != null && covered < g.credits()) {
			needs.add(new Need(g.code(), g.title(), g.credits() - covered, descendantModules(g)));
		}
		return needs;
	}

	private List<String> descendantModules(Group g) {
		Set<String> modules = new LinkedHashSet<>(g.modules());
		for (Group d : this.programme.groups()) {
			if (d.code().startsWith(g.code() + ".")) {
				modules.addAll(d.modules());
			}
		}
		return List.copyOf(modules);
	}

	// ---------------------------------------------------------------- planning

	/** Builds a plan for the request. */
	public StudyPlan plan(PlanRequest request) {
		Set<String> completed = new HashSet<>(request.completed());
		Set<String> preferred = new HashSet<>(request.preferred());
		Map<String, String> chosen = select(request.focus(), completed, preferred);
		List<List<String>> semesters = schedule(chosen.keySet(), completed, request);
		rebalance(chosen, semesters, completed, preferred, request);
		return evaluate(request.focus(), request.start(), semesters, completed, request.maxCreditsPerSemester());
	}

	/** Chooses modules for every requirement: module code → requirement code. */
	private Map<String, String> select(String focus, Set<String> completed, Set<String> preferred) {
		Map<String, String> chosen = new LinkedHashMap<>();
		List<Need> needs = new ArrayList<>(needs(focus));
		// The most constrained requirements pick first, so they keep the modules only they can use.
		needs.sort(Comparator.comparingInt((Need n) -> n.modules().size()));
		for (Need need : needs) {
			List<Module> candidates = need.modules().stream()
				.filter((c) -> !chosen.containsKey(c))
				.map((c) -> this.programme.modules().get(c))
				.filter((m) -> m != null && m.credits() > 0)
				.sorted(Comparator.comparingDouble((Module m) -> -score(m, completed, preferred)).thenComparing(Module::code))
				.limit(14)
				.toList();
			for (Module m : bestFit(candidates, need.credits(), completed, preferred)) {
				chosen.put(m.code(), need.code());
			}
		}
		ensureOralExam(chosen, focus, completed, preferred);
		return chosen;
	}

	private double score(Module m, Set<String> completed, Set<String> preferred) {
		double score = 0;
		if (completed.contains(m.code())) {
			score += 100;
		}
		if (preferred.contains(m.code())) {
			score += 50;
		}
		if (m.offered() != Offered.UNKNOWN) {
			score += 5;
		}
		ModuleHistory h = this.history.get(m.code());
		if (h != null) {
			Double rate = h.passRate();
			if (rate != null) {
				score += rate * 3;
			}
			if (h.terms().stream().noneMatch(ModuleHistory.Term::offered)) {
				score -= 20;
			}
		}
		return score;
	}

	/**
	 * The subset of candidates that reaches the credits with the least overshoot, preferring higher
	 * scores (candidates come sorted by score). Small sets only, so trying all subsets is fine.
	 */
	private List<Module> bestFit(List<Module> candidates, int credits, Set<String> completed, Set<String> preferred) {
		if (credits <= 0 || candidates.isEmpty()) {
			return List.of();
		}
		int n = candidates.size();
		long best = -1;
		int bestOver = Integer.MAX_VALUE;
		double bestScore = Double.NEGATIVE_INFINITY;
		int bestCount = Integer.MAX_VALUE;
		for (long mask = 1; mask < (1L << n); mask++) {
			int sum = 0;
			double score = 0;
			int count = 0;
			for (int i = 0; i < n; i++) {
				if ((mask & (1L << i)) != 0) {
					sum += candidates.get(i).credits();
					score += score(candidates.get(i), completed, preferred);
					count++;
				}
			}
			if (sum < credits) {
				continue;
			}
			int over = sum - credits;
			boolean better = over < bestOver || (over == bestOver && count < bestCount)
					|| (over == bestOver && count == bestCount && score > bestScore);
			if (better) {
				best = mask;
				bestOver = over;
				bestScore = score;
				bestCount = count;
			}
		}
		if (best < 0) {
			return candidates;   // not enough modules: take all, the evaluation reports what is missing
		}
		List<Module> picked = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if ((best & (1L << i)) != 0) {
				picked.add(candidates.get(i));
			}
		}
		return picked;
	}

	/** The regulations require at least one module with an oral exam in the areas and the focus. */
	private void ensureOralExam(Map<String, String> chosen, String focus, Set<String> completed, Set<String> preferred) {
		if (chosen.entrySet().stream().anyMatch((e) -> countsForOral(e.getValue()) && module(e.getKey()).oralExam())) {
			return;
		}
		for (Need need : needs(focus)) {
			if (!countsForOral(need.code())) {
				continue;
			}
			for (Map.Entry<String, String> e : List.copyOf(chosen.entrySet())) {
				if (!e.getValue().equals(need.code()) || completed.contains(e.getKey())) {
					continue;
				}
				int credits = module(e.getKey()).credits();
				for (String c : need.modules()) {
					Module m = module(c);
					if (m != null && !chosen.containsKey(c) && m.oralExam() && m.credits() == credits) {
						chosen.remove(e.getKey());
						chosen.put(c, need.code());
						return;
					}
				}
			}
		}
	}

	private static boolean countsForOral(String needCode) {
		return needCode.matches("[1-4](\\..*)?") || needCode.startsWith("8.");
	}

	/** Places the chosen modules into semesters; completed ones stay out, the thesis goes last. */
	private List<List<String>> schedule(Set<String> chosen, Set<String> completed, PlanRequest request) {
		int count = Math.max(1, request.semesters());
		List<List<String>> semesters = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			semesters.add(new ArrayList<>());
		}
		Set<String> thesis = new HashSet<>(thesisModules());
		List<Module> toPlace = chosen.stream()
			.filter((c) -> !completed.contains(c) && !thesis.contains(c))
			.map(this::module)
			.sorted(Comparator.comparingInt((Module m) -> (m.offered() == Offered.WINTER || m.offered() == Offered.SUMMER) ? 0 : 1)
				.thenComparing(Comparator.comparingInt(Module::credits).reversed())
				.thenComparing(Module::code))
			.toList();
		int courseSemesters = Math.max(1, count - 1);
		int remaining = toPlace.stream().mapToInt(Module::credits).sum();
		int capacity = (request.maxCreditsPerSemester() != null) ? request.maxCreditsPerSemester()
				: (int) Math.ceil(remaining / (double) courseSemesters) + 4;
		boolean[] winter = winterFlags(request.start(), count);
		int[] load = new int[count];
		for (Module m : toPlace) {
			int target = pick(m, winter, load, courseSemesters, capacity, true);
			if (target < 0) {
				target = pick(m, winter, load, courseSemesters, Integer.MAX_VALUE, true);   // over the comfortable load
			}
			if (target < 0) {
				target = pick(m, winter, load, count, Integer.MAX_VALUE, false);           // wrong term: reported
			}
			semesters.get(target).add(m.code());
			load[target] += m.credits();
		}
		for (String c : chosen) {
			if (thesis.contains(c) && !completed.contains(c)) {
				semesters.get(count - 1).add(c);
			}
		}
		return semesters;
	}

	/**
	 * Evens out overloaded semesters: a module in a semester above the comfortable load is swapped
	 * for another module of the same requirement and credits that can go into a lighter semester.
	 * Happens when many modules of the choice are only taught in the same term. Completed,
	 * preferred and thesis modules stay.
	 */
	private void rebalance(Map<String, String> chosen, List<List<String>> semesters, Set<String> completed,
			Set<String> preferred, PlanRequest request) {
		int count = semesters.size();
		int courseSemesters = Math.max(1, count - 1);
		boolean[] winter = winterFlags(request.start(), count);
		Set<String> thesis = new HashSet<>(thesisModules());
		int total = 0;
		for (int i = 0; i < courseSemesters; i++) {
			total += credits(semesters.get(i));
		}
		int target = (request.maxCreditsPerSemester() != null) ? request.maxCreditsPerSemester()
				: (int) Math.ceil(total / (double) courseSemesters);
		Map<String, List<String>> needModules = new HashMap<>();
		for (Need need : needs(request.focus())) {
			needModules.put(need.code(), need.modules());
		}
		for (int round = 0; round < 40; round++) {
			int heavy = -1;
			for (int i = 0; i < courseSemesters; i++) {
				if (credits(semesters.get(i)) > target && (heavy < 0 || credits(semesters.get(i)) > credits(semesters.get(heavy)))) {
					heavy = i;
				}
			}
			if (heavy < 0 || !swapOut(heavy, chosen, semesters, winter, courseSemesters, needModules, thesis, preferred)) {
				return;
			}
		}
	}

	private boolean swapOut(int heavy, Map<String, String> chosen, List<List<String>> semesters, boolean[] winter,
			int courseSemesters, Map<String, List<String>> needModules, Set<String> thesis, Set<String> preferred) {
		for (String code : List.copyOf(semesters.get(heavy))) {
			if (thesis.contains(code) || preferred.contains(code)) {
				continue;
			}
			Module current = module(code);
			// A module taught in both terms can simply move.
			for (int j = 0; j < courseSemesters; j++) {
				if (j != heavy && current.offered().in(winter[j]) && lighter(semesters, j, heavy, current.credits())) {
					semesters.get(heavy).remove(code);
					semesters.get(j).add(code);
					return true;
				}
			}
			String need = chosen.get(code);
			for (String alternative : needModules.getOrDefault(need, List.of())) {
				Module m = module(alternative);
				if (m == null || chosen.containsKey(alternative) || m.credits() != current.credits()) {
					continue;
				}
				for (int j = 0; j < courseSemesters; j++) {
					if (j != heavy && m.offered() != Offered.UNKNOWN && m.offered().in(winter[j])
							&& lighter(semesters, j, heavy, m.credits())) {
						chosen.remove(code);
						chosen.put(alternative, need);
						semesters.get(heavy).remove(code);
						semesters.get(j).add(alternative);
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Moving this many credits from the heavy semester to semester j makes the heavy one lighter without making j the new peak. */
	private boolean lighter(List<List<String>> semesters, int j, int heavy, int credits) {
		return credits(semesters.get(j)) + credits < credits(semesters.get(heavy));
	}

	private int credits(List<String> codes) {
		return codes.stream().map(this::module).filter((m) -> m != null).mapToInt(Module::credits).sum();
	}

	/** The least loaded semester among the first {@code limit} that fits, or -1. */
	private static int pick(Module m, boolean[] winter, int[] load, int limit, int capacity, boolean matchTerm) {
		int best = -1;
		for (int i = 0; i < limit; i++) {
			if (matchTerm && !m.offered().in(winter[i])) {
				continue;
			}
			if (load[i] + m.credits() > capacity) {
				continue;
			}
			if (best < 0 || load[i] < load[best]) {
				best = i;
			}
		}
		return best;
	}

	private List<String> thesisModules() {
		Group thesis = this.programme.group(THESIS_GROUP);
		return (thesis != null) ? thesis.modules() : List.of();
	}

	// ---------------------------------------------------------------- checking

	/**
	 * Checks a plan: how far each requirement is met, credits per semester, and everything the
	 * student should know (wrong term, heavy semesters, thesis too early, no oral exam, …).
	 */
	public StudyPlan evaluate(String focus, String start, List<List<String>> semesters, Set<String> completed,
			Integer maxCreditsPerSemester) {
		boolean[] winter = winterFlags(start, semesters.size());
		List<String> placed = new ArrayList<>(completed);
		semesters.forEach(placed::addAll);
		Map<String, String> assignment = assign(focus, placed);
		List<StudyPlan.Warning> warnings = new ArrayList<>();
		List<StudyPlan.Note> notes = new ArrayList<>();

		List<StudyPlan.Semester> result = new ArrayList<>();
		int creditsBefore = completed.stream().map(this::module).filter((m) -> m != null).mapToInt(Module::credits).sum();
		Set<String> thesis = new HashSet<>(thesisModules());
		for (int i = 0; i < semesters.size(); i++) {
			List<StudyPlan.Planned> modules = new ArrayList<>();
			int credits = 0;
			for (String code : semesters.get(i)) {
				Module m = module(code);
				if (m == null) {
					continue;
				}
				credits += m.credits();
				modules.add(new StudyPlan.Planned(m.code(), m.title(), m.credits(), assignment.get(code), m.offered().name(),
						m.oralExam(), thesis.contains(code), m.url()));
				if (!m.offered().in(winter[i])) {
					warnings.add(StudyPlan.Warning.of("WRONG_TERM", m.code(), String.valueOf(i + 1), m.offered().name()));
				}
				else if (m.offered() == Offered.UNKNOWN) {
					warnings.add(StudyPlan.Warning.of("UNKNOWN_TERM", m.code()));
				}
				ModuleHistory h = this.history.get(code);
				if (h != null && !h.terms().isEmpty() && h.terms().stream().noneMatch(ModuleHistory.Term::offered)) {
					warnings.add(StudyPlan.Warning.of("NOT_OFFERED_RECENTLY", m.code()));
				}
				if (thesis.contains(code) && creditsBefore < this.programme.thesisMinCredits()) {
					warnings.add(StudyPlan.Warning.of("THESIS_TOO_EARLY", String.valueOf(i + 1), String.valueOf(creditsBefore),
							String.valueOf(this.programme.thesisMinCredits())));
				}
			}
			int heavy = (maxCreditsPerSemester != null) ? maxCreditsPerSemester : HEAVY;
			if (credits > heavy) {
				warnings.add(StudyPlan.Warning.of("HEAVY_SEMESTER", String.valueOf(i + 1), String.valueOf(credits)));
			}
			creditsBefore += credits;
			result.add(new StudyPlan.Semester(i + 1, termId(start, i), winter[i], credits, modules));
		}

		List<StudyPlan.Requirement> requirements = new ArrayList<>();
		for (Need need : needs(focus)) {
			int got = assignment.entrySet().stream()
				.filter((e) -> e.getValue().equals(need.code()))
				.mapToInt((e) -> module(e.getKey()).credits())
				.sum();
			requirements.add(new StudyPlan.Requirement(need.code(), need.title(), need.credits(), got, got >= need.credits()));
			if (got < need.credits()) {
				warnings.add(StudyPlan.Warning.of("REQUIREMENT_UNMET", need.code(), String.valueOf(need.credits() - got)));
			}
		}
		boolean oral = assignment.entrySet().stream().anyMatch((e) -> countsForOral(e.getValue()) && module(e.getKey()).oralExam());
		if (!oral) {
			warnings.add(StudyPlan.Warning.of("NO_ORAL_EXAM"));
		}
		int total = placed.stream().distinct().map(this::module).filter((m) -> m != null).mapToInt(Module::credits).sum();
		if (completed.isEmpty() && !semesters.isEmpty()) {
			notes.add(StudyPlan.Note.of("MENTORING"));
		}
		if (this.programme.thesisWeeks() > 0) {
			notes.add(StudyPlan.Note.of("THESIS_WEEKS", String.valueOf(this.programme.thesisWeeks()),
					String.valueOf(this.programme.thesisMinCredits())));
		}
		return new StudyPlan(this.programme.id(), focus, start, total, this.programme.totalCredits(), result,
				requirements, warnings, notes, List.copyOf(completed));
	}

	/** Which requirement each planned module counts for; every module counts once. */
	private Map<String, String> assign(String focus, List<String> placed) {
		Map<String, String> assignment = new HashMap<>();
		List<Need> needs = new ArrayList<>(needs(focus));
		needs.sort(Comparator.comparingInt((Need n) -> n.modules().size()));
		Map<String, Integer> got = new HashMap<>();
		for (Need need : needs) {
			for (String code : placed) {
				if (assignment.containsKey(code) || !need.modules().contains(code) || module(code) == null) {
					continue;
				}
				if (got.getOrDefault(need.code(), 0) >= need.credits()) {
					break;
				}
				assignment.put(code, need.code());
				got.merge(need.code(), module(code).credits(), Integer::sum);
			}
		}
		return assignment;
	}

	private Module module(String code) {
		return this.programme.modules().get(code);
	}

	// ---------------------------------------------------------------- terms

	/** Whether each semester of the plan is a winter semester, from the start term (e.g. WS2026). */
	static boolean[] winterFlags(String start, int count) {
		boolean[] flags = new boolean[count];
		boolean winter = start == null || start.startsWith("WS");
		for (int i = 0; i < count; i++) {
			flags[i] = winter;
			winter = !winter;
		}
		return flags;
	}

	/** The term id of the i-th semester (0-based) after the start, e.g. WS2026, SS2027, WS2027. */
	static String termId(String start, int i) {
		Matcher m = (start != null) ? TERM.matcher(start) : null;
		if (m == null || !m.matches()) {
			return null;
		}
		int index = Integer.parseInt(m.group(2)) * 2 + ("WS".equals(m.group(1)) ? 1 : 0) + i;
		return ((index % 2 == 1) ? "WS" : "SS") + (index / 2);
	}

}
