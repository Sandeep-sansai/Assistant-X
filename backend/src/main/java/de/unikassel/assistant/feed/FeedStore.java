package de.unikassel.assistant.feed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import de.unikassel.assistant.feed.FeedParser.ParsedItem;
import de.unikassel.assistant.feed.FeedProperties.Kind;
import de.unikassel.assistant.feed.FeedProperties.Source;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class FeedStore {

	private final JdbcClient jdbc;

	FeedStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean isEmpty() {
		return this.jdbc.sql("SELECT NOT EXISTS (SELECT 1 FROM feed_item)").query(Boolean.class).single();
	}

	@Transactional
	void save(Source source, List<ParsedItem> items, Instant now) {
		for (ParsedItem item : items) {
			this.jdbc.sql("""
					INSERT INTO feed_item (source_id, department, kind, title, url, category, item_date, first_seen_at, last_seen_at)
					VALUES (:source, :department, :kind, :title, :url, :category, :date, :now, :now)
					ON CONFLICT (source_id, url) DO UPDATE SET
					    department = excluded.department, kind = excluded.kind, title = excluded.title,
					    category = excluded.category, item_date = excluded.item_date, last_seen_at = excluded.last_seen_at
					""")
				.param("source", source.id())
				.param("department", source.department())
				.param("kind", source.kind().name())
				.param("title", item.title())
				.param("url", item.url())
				.param("category", item.category())
				.param("date", item.date())
				.param("now", utc(now))
				.update();
		}
	}

	/**
	 * Items of one kind dated within [from, to]: those of the given department, or the
	 * university-wide ones when the department is null.
	 */
	List<StoredItem> find(Kind kind, String department, LocalDate from, LocalDate to, boolean newestFirst, int limit) {
		return this.jdbc.sql("""
				SELECT source_id, department, title, url, category, item_date, first_seen_at
				FROM feed_item
				WHERE kind = :kind
				  AND department IS NOT DISTINCT FROM CAST(:department AS TEXT)
				  AND item_date BETWEEN :from AND :to
				ORDER BY item_date %s, first_seen_at DESC
				LIMIT :limit
				""".formatted(newestFirst ? "DESC" : "ASC"))
			.param("kind", kind.name())
			.param("department", department)
			.param("from", from)
			.param("to", to)
			.param("limit", limit)
			.query((rs, i) -> new StoredItem(rs.getString("source_id"), rs.getString("department"), rs.getString("title"),
					rs.getString("url"), rs.getString("category"), rs.getObject("item_date", LocalDate.class),
					rs.getObject("first_seen_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	record StoredItem(String sourceId, String department, String title, String url, String category, LocalDate date,
			Instant firstSeenAt) {
	}

}
