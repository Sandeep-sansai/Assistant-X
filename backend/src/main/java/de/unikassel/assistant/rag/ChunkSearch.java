package de.unikassel.assistant.rag;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Keyword retrieval with PostgreSQL full-text search, using both the German and the English
 * stemmer. This is the v1 baseline; vector search is meant to be added next to it, not replace it
 * (see docs/decisions.md).
 */
@Repository
class ChunkSearch {

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}-]{2,}");

	private static final int MAX_TERMS = 30;

	/**
	 * Question words that say nothing about the topic. The German search configuration doesn't know
	 * English stop words, so "the", "what" and "does" would otherwise make any English page match.
	 */
	private static final Set<String> STOP = Set.of("the", "and", "what", "does", "how", "much", "many", "is", "are", "was",
			"were", "can", "could", "should", "would", "will", "do", "did", "have", "has", "had", "for", "with", "about", "from",
			"into", "this", "that", "these", "those", "there", "their", "they", "you", "your", "our", "my", "mine", "me", "who",
			"whom", "which", "when", "where", "why", "any", "some", "all", "get", "got", "need", "want", "know", "tell", "please",
			"also", "then", "than", "but", "not", "if", "it", "its", "be", "been", "an", "a", "of", "on", "in", "at", "to", "or",
			"include", "includes", "happen", "happens", "happened", "der", "die", "das", "und", "ist", "sind", "wie",
			"wer", "wann", "wo", "warum", "welche", "welcher", "welches", "ich", "mein", "meine", "kann", "können", "muss",
			"müssen", "gibt", "es", "ein", "eine", "einen", "für", "mit", "von", "auf", "bei", "zum", "zur", "den", "dem", "des",
			"im", "am", "oder", "auch", "noch", "bitte");

	private final JdbcClient jdbc;

	ChunkSearch(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<RetrievedChunk> search(String question, int limit) {
		String query = orQuery(question);
		if (query.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				WITH q AS (
				    SELECT websearch_to_tsquery('german', :q) AS de, websearch_to_tsquery('english', :q) AS en
				)
				SELECT c.heading, c.text, d.title, d.url, d.fetched_at,
				       -- Length-normalised text match, plus a strong bonus when the page's title or address
				       -- names the topic ("Semesterbeitrag"); attached files (long reports, PDFs) count less.
				       (greatest(ts_rank_cd(c.tsv_de, q.de, 1), ts_rank_cd(c.tsv_en, q.en, 1))
				         + 2 * ts_rank_cd(to_tsvector('german', coalesce(d.title, '') || ' '
				               || regexp_replace(d.url, '[/_.\\-]+', ' ', 'g')), q.de))
				       * (CASE WHEN d.url LIKE '%/files/%' THEN 0.7 ELSE 1 END) AS score
				FROM chunk c
				JOIN document d ON d.id = c.document_id
				CROSS JOIN q
				WHERE d.status = 'active' AND (c.tsv_de @@ q.de OR c.tsv_en @@ q.en)
				ORDER BY score DESC
				LIMIT :limit
				""")
			.param("q", query)
			.param("limit", limit)
			.query((rs, i) -> new RetrievedChunk(rs.getString("heading"), rs.getString("text"), rs.getString("title"),
					rs.getString("url"), rs.getObject("fetched_at", OffsetDateTime.class), rs.getDouble("score")))
			.list();
	}

	/**
	 * Matches chunks containing any of the question's words; ranking then prefers chunks that
	 * contain more of them. Requiring all words would miss most answers to natural questions.
	 */
	static String orQuery(String question) {
		Set<String> terms = new LinkedHashSet<>();
		Matcher m = WORD.matcher(question.toLowerCase(Locale.ROOT));
		while (m.find() && terms.size() < MAX_TERMS) {
			if (!STOP.contains(m.group())) {
				terms.add(m.group());
			}
		}
		return String.join(" or ", new ArrayList<>(terms));
	}

	record RetrievedChunk(String heading, String text, String title, String url, OffsetDateTime fetchedAt,
			double score) {
	}

}
