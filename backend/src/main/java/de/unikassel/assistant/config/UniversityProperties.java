package de.unikassel.assistant.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * University-specific values. Only the parts the dashboard needs so far; the Kiro spec
 * (Requirement 12) adds the rest when accounts are built.
 */
@ConfigurationProperties("assistant.university")
public record UniversityProperties(String timeZone, List<Department> departments) {

	public UniversityProperties {
		timeZone = (timeZone == null || timeZone.isBlank()) ? "Europe/Berlin" : timeZone;
		departments = departments == null ? List.of() : departments;
	}

	public ZoneId zone() {
		return ZoneId.of(this.timeZone);
	}

	/** @param programmes suggestions for the profile picker; students may also type their own */
	public record Department(String id, String name, List<String> programmes) {

		public Department {
			programmes = programmes == null ? List.of() : programmes;
		}

	}

}
