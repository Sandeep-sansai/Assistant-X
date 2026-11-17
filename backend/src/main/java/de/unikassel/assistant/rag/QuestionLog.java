package de.unikassel.assistant.rag;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Anonymous log of questions and answers, used to find what students ask most and where the
 * assistant fails. Stores nothing that identifies the person asking.
 */
@Repository
public class QuestionLog {

	private final JdbcClient jdbc;

	QuestionLog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	long record(String question, String answer, boolean hadSources, List<String> sourceUrls, Double topScore,
			String model, int latencyMs) {
		return this.jdbc.sql("""
				INSERT INTO question (question, answer, had_sources, source_urls, top_score, model, latency_ms)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				RETURNING id
				""")
			.params(question, answer, hadSources, sourceUrls.toArray(String[]::new), topScore, model, latencyMs)
			.query(Long.class)
			.single();
	}

	/** Returns false if the question doesn't exist. */
	public boolean addFeedback(long questionId, int rating, String comment) {
		return this.jdbc.sql("""
				INSERT INTO feedback (question_id, rating, comment)
				SELECT id, ?, ? FROM question WHERE id = ?
				""").params(rating, comment, questionId).update() > 0;
	}

}
