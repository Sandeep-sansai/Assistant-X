package de.unikassel.assistant.timetable;

import java.util.LinkedHashMap;
import java.util.Map;

import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.Programme.Offered;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableServiceTests {

	private static final Map<String, Module> MODULES = new LinkedHashMap<>();

	static {
		module("PI-11", "Pattern Recognition and Machine Learning I");
		module("PI-12", "Pattern Recognition and Machine Learning II");
		module("PI-03", "Begriffliche Datenanalyse");
		module("TI-01", "Automaten, Spiele, Logik");
		module("LN-01", "Labor Netzwerke");
		module("PI-26", "Deep Learning");
		module("TeI-08", "Deep Learning");
		module("PI-25", "Labor Deep Learning");
	}

	private static void module(String code, String title) {
		MODULES.put(code, new Module(code, title, 6, Offered.WINTER, "", "", 1, false, "", "", ""));
	}

	@Test
	void matchesTheCatalogueTitleToTheModule() {
		assertThat(TimetableService.match("Pattern Recognition and Machine Learning I", MODULES)).isEqualTo("PI-11");
		assertThat(TimetableService.match("Pattern Recognition and Machine Learning II (Übung)", MODULES)).isEqualTo("PI-12");
		assertThat(TimetableService.match("Vorlesung: Begriffliche Datenanalyse", MODULES)).isEqualTo("PI-03");
		assertThat(TimetableService.match("Übung Automaten, Spiele, Logik", MODULES)).isEqualTo("TI-01");
		assertThat(TimetableService.match("Labor Netzwerke", MODULES)).isEqualTo("LN-01");
		assertThat(TimetableService.match("Übungen zu Automaten, Spiele, Logik Inform-4410-Ü1", MODULES)).isEqualTo("TI-01");
		assertThat(TimetableService.match("Begriffliche Datenanalyse Inform-3020-V1", MODULES)).isEqualTo("PI-03");
	}

	@Test
	void countsACourseForEveryModuleWithItsName() {
		assertThat(TimetableService.matchAll("Deep Learning Inform-1840-V1", MODULES)).containsExactly("PI-26", "TeI-08");
		assertThat(TimetableService.matchAll("Labor Deep Learning Inform-1850-V1", MODULES)).containsExactly("PI-25");
	}

	@Test
	void usesTheModuleCodeWhenTheTitleNamesIt() {
		assertThat(TimetableService.match("TI-01 Automata (exercise group 2)", MODULES)).isEqualTo("TI-01");
	}

	@Test
	void leavesUnrelatedCoursesUnmatched() {
		assertThat(TimetableService.match("Einführung in die Programmierung", MODULES)).isNull();
		assertThat(TimetableService.match("Machine Learning", MODULES)).isNull();
	}

}
