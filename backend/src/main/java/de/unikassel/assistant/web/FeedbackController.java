package de.unikassel.assistant.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Feedback from testers about the app itself (not about one answer; that is /api/feedback/{id}). */
@RestController
@RequestMapping("/api")
class FeedbackController {

	private final JdbcClient jdbc;

	FeedbackController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/site-feedback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void send(@Valid @RequestBody SiteFeedback body) {
		this.jdbc.sql("INSERT INTO site_feedback (message, lang) VALUES (?, ?)")
			.params(body.message().strip(), body.lang())
			.update();
	}

	List<Entry> latest(int limit) {
		return this.jdbc.sql("SELECT message, lang, created_at FROM site_feedback ORDER BY created_at DESC LIMIT ?")
			.param(limit)
			.query((rs, n) -> new Entry(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()))
			.list();
	}

	record SiteFeedback(@NotBlank @Size(max = 2000) String message, @Pattern(regexp = "en|de") String lang) {
	}

	record Entry(String message, String lang, Instant at) {
	}

}
