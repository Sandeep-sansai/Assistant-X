package de.unikassel.assistant.programme;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Programmes the study planner supports, and where the university's past module data lives once
 * it is available.
 *
 * @param historyFile optional CSV with past module offerings and results (see
 * docs/past-data-request.md); empty to plan from the regulations only
 */
@ConfigurationProperties("assistant.planner")
public record ProgrammeProperties(String cron, String historyFile, List<Source> programmes) {

	public ProgrammeProperties {
		programmes = (programmes == null) ? List.of() : programmes;
	}

	/**
	 * @param url the regulations page in the module database (showFPO.php?id=…)
	 * @param department department id the programme belongs to
	 * @param names programme names from the profile picker that mean this programme
	 */
	public record Source(String id, String title, String department, List<String> names, String url) {

		public Source {
			names = (names == null) ? List.of() : names;
		}

	}

}
