package de.unikassel.assistant.ingest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import de.unikassel.assistant.ingest.Chunker.ChunkDraft;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class DocumentStore {

	private final JdbcClient jdbc;

	DocumentStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<String> contentHash(String url) {
		return this.jdbc.sql("SELECT content_hash FROM document WHERE url = ?").param(url).query(String.class).optional();
	}

	/** Records that an unchanged document was seen again. */
	void touch(String url, Instant now) {
		this.jdbc.sql("UPDATE document SET fetched_at = ?, status = 'active' WHERE url = ?")
			.params(utc(now), url)
			.update();
	}

	/** Inserts or replaces a document and all of its chunks. */
	@Transactional
	void save(String url, String title, String contentType, String hash, List<ChunkDraft> chunks, Instant now) {
		long id = this.jdbc.sql("""
				INSERT INTO document (url, title, content_type, content_hash, fetched_at, changed_at, status)
				VALUES (:url, :title, :type, :hash, :now, :now, 'active')
				ON CONFLICT (url) DO UPDATE SET
				    title = excluded.title, content_type = excluded.content_type,
				    content_hash = excluded.content_hash, fetched_at = excluded.fetched_at,
				    changed_at = excluded.changed_at, status = 'active'
				RETURNING id
				""")
			.param("url", url)
			.param("title", title)
			.param("type", contentType)
			.param("hash", hash)
			.param("now", utc(now))
			.query(Long.class)
			.single();
		this.jdbc.sql("DELETE FROM chunk WHERE document_id = ?").param(id).update();
		for (int i = 0; i < chunks.size(); i++) {
			this.jdbc.sql("INSERT INTO chunk (document_id, ordinal, heading, text) VALUES (?, ?, ?, ?)")
				.params(id, i, chunks.get(i).heading(), chunks.get(i).text())
				.update();
		}
	}

	/** Marks documents the last complete crawl did not reach, so they stop being cited. */
	int markGoneIfNotSeenSince(Instant runStart) {
		return this.jdbc.sql("UPDATE document SET status = 'gone' WHERE status = 'active' AND fetched_at < ?")
			.param(utc(runStart))
			.update();
	}

	long startRun(Instant now) {
		return this.jdbc.sql("INSERT INTO ingestion_run (started_at) VALUES (?) RETURNING id")
			.param(utc(now))
			.query(Long.class)
			.single();
	}

	void finishRun(long runId, String status, int fetched, int changed, int errors, Instant now) {
		this.jdbc.sql("""
				UPDATE ingestion_run
				SET finished_at = ?, status = ?, pages_fetched = ?, pages_changed = ?, errors = ?
				WHERE id = ?
				""").params(utc(now), status, fetched, changed, errors, runId).update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
