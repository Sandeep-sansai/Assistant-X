package de.unikassel.assistant.web;

import java.util.Arrays;
import java.util.List;

import de.unikassel.assistant.timetable.TimetableService;
import de.unikassel.assistant.timetable.TimetableService.StoredSlot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Course times for the planner's week view; importing them is an admin action (AdminController). */
@RestController
@RequestMapping("/api")
class TimetableController {

	private final TimetableService timetable;

	TimetableController(TimetableService timetable) {
		this.timetable = timetable;
	}

	/**
	 * @param terms comma-separated semesters, e.g. "WS2026,SS2027"
	 * @return the stored times and which semesters have any (so the page can fall back to the
	 * latest semester of the same season as an estimate)
	 */
	@GetMapping("/timetable")
	Timetable timetable(@RequestParam String programme, @RequestParam(defaultValue = "") String terms) {
		List<String> wanted = Arrays.stream(terms.split(",")).map(String::strip).filter((t) -> !t.isEmpty()).limit(12).toList();
		List<String> available = this.timetable.terms(programme);
		return new Timetable(available, this.timetable.slots(programme, wanted.isEmpty() ? available : wanted));
	}

	record Timetable(List<String> available, List<StoredSlot> slots) {
	}

}
