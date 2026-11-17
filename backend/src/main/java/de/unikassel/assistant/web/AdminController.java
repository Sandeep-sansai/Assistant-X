package de.unikassel.assistant.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import de.unikassel.assistant.config.AssistantProperties;
import de.unikassel.assistant.feed.FeedService;
import de.unikassel.assistant.ingest.IngestionService;
import de.unikassel.assistant.programme.ProgrammeService;
import de.unikassel.assistant.timetable.TimetableService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final IngestionService ingestion;

	private final FeedService feed;

	private final ProgrammeService programmes;

	private final FeedbackController feedback;

	private final TimetableService timetable;

	private final String adminToken;

	AdminController(IngestionService ingestion, FeedService feed, ProgrammeService programmes,
			FeedbackController feedback, TimetableService timetable, AssistantProperties props) {
		this.ingestion = ingestion;
		this.feed = feed;
		this.programmes = programmes;
		this.feedback = feedback;
		this.timetable = timetable;
		this.adminToken = props.adminToken();
	}

	/**
	 * Replaces one semester's course times of a programme, as read from the eCampus course
	 * catalogue by the import tool (docs/decisions.md #20).
	 */
	@PostMapping("/timetable")
	ResponseEntity<?> importTimetable(@RequestHeader(name = "X-Admin-Token", required = false) String token,
			@RequestBody TimetableImport body) {
		ResponseEntity<String> denied = checkToken(token);
		if (denied != null) {
			return denied;
		}
		try {
			return ResponseEntity.ok(this.timetable.replace(body.programme(), body.term(), body.courses()));
		}
		catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(ex.getMessage());
		}
	}

	record TimetableImport(String programme, String term, java.util.List<TimetableService.Course> courses) {
	}

	/** The latest feedback testers sent from the page. */
	@GetMapping("/feedback")
	ResponseEntity<?> feedback(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		ResponseEntity<String> denied = checkToken(token);
		return (denied != null) ? denied : ResponseEntity.ok(this.feedback.latest(200));
	}

	/** Re-imports the study planner's programmes from the module database now. */
	@PostMapping("/programmes/import")
	ResponseEntity<String> importProgrammes(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		ResponseEntity<String> denied = checkToken(token);
		if (denied != null) {
			return denied;
		}
		return this.programmes.startImport() ? ResponseEntity.accepted().body("Programme import started.")
				: ResponseEntity.status(HttpStatus.CONFLICT).body("Programme import is already running.");
	}

	/** Starts a crawl now instead of waiting for the schedule. */
	@PostMapping("/ingest")
	ResponseEntity<String> ingest(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		ResponseEntity<String> denied = checkToken(token);
		if (denied != null) {
			return denied;
		}
		return this.ingestion.startInBackground() ? ResponseEntity.accepted().body("Ingestion started.")
				: ResponseEntity.status(HttpStatus.CONFLICT).body("Ingestion is already running.");
	}

	/** Re-reads the news and event listing pages now. */
	@PostMapping("/feed/refresh")
	ResponseEntity<String> refreshFeed(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		ResponseEntity<String> denied = checkToken(token);
		if (denied != null) {
			return denied;
		}
		return this.feed.startInBackground() ? ResponseEntity.accepted().body("Feed refresh started.")
				: ResponseEntity.status(HttpStatus.CONFLICT).body("Feed refresh is already running.");
	}

	/** Returns the error response, or null if the token is valid. */
	private ResponseEntity<String> checkToken(String token) {
		if (this.adminToken == null || this.adminToken.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin endpoints are disabled: set ADMIN_TOKEN.");
		}
		if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
				this.adminToken.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return null;
	}

}
