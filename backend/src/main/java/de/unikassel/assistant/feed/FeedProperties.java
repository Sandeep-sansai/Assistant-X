package de.unikassel.assistant.feed;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The listing pages the news feed is read from. */
@ConfigurationProperties("assistant.feed")
public record FeedProperties(List<Source> sources) {

	public FeedProperties {
		sources = sources == null ? List.of() : sources;
	}

	public boolean covers(String department) {
		return department != null && this.sources.stream().anyMatch((s) -> department.equals(s.department()));
	}

	/**
	 * @param department a department id from {@code assistant.university.departments}, or null for
	 * university-wide sources
	 */
	public record Source(String id, String label, String department, Kind kind, String url) {
	}

	public enum Kind {

		NEWS, EVENT

	}

}
