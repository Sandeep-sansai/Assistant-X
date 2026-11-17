package de.unikassel.assistant.programme;

import de.unikassel.assistant.programme.Programme.Group;
import de.unikassel.assistant.programme.Programme.Module;
import de.unikassel.assistant.programme.Programme.Offered;
import de.unikassel.assistant.programme.ProgrammeParser.ModuleRef;
import de.unikassel.assistant.programme.ProgrammeParser.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgrammeParserTests {

	private final ProgrammeParser parser = new ProgrammeParser();

	/** Shape copied from showFPO.php?id=120 (Computer Science M.Sc., 2026). */
	private static final String FPO = """
			<html><body>
			<p>§ 3 Regelstudienzeit, Umfang des Studiums (1) Die Regelstudienzeit für das Masterstudium beträgt
			einschließlich des Masterabschlussmoduls vier Semester. (2) Für den erfolgreich abgeschlossenen
			Masterstudiengang werden insgesamt 120 Credits vergeben. Davon entfallen 30 Credits auf das Masterabschlussmodul.</p>
			<p>§ 10 (2) Das Thema der Masterarbeit wird auf Antrag frühestens ausgegeben, sobald die oder der Studierende
			erfolgreiche Prüfungsleistungen im Umfang von mindestens 60 CP absolviert hat. (4) Die Bearbeitungszeit der
			Masterarbeit beträgt 24 Wochen und beginnt mit dem Tag der Bekanntgabe des Themas.</p>
			<table><tr><th>Nummer</th><th>Studienbereich</th><th>Module</th><th>Credits</th></tr>
			<tr><td>1</td><td>Wahlpflicht Praktische Informatik</td><td>Siehe Plan</td><td>6 CP</td></tr></table>
			<table>
			<tr><th>Nummer</th><th>Modulname</th><th>Credits</th></tr>
			<tr><td colspan="3">Wahlpflichtbereiche</td></tr>
			<tr><td colspan="3">1.: Wahlpflicht Praktische Informatik (6 cp)</td></tr>
			<tr><td><a href="showImportmodule.php?id=1">PI-01</a></td><td>Software Architecture</td><td>6</td></tr>
			<tr><td><a href="showImportmodule.php?id=2">PI-02</a></td><td>Databases II</td><td>6</td></tr>
			<tr><td colspan="3">8.1: Schwerpunkt Computational Intelligence &amp; Data Analytics (CIDA) (48 cp)</td></tr>
			<tr><td colspan="3">8.1.1: Foundational Courses (18 cp)</td></tr>
			<tr><td><a href="showModule.php?id=3">CIDA-01</a></td><td>Machine Learning</td><td>6</td></tr>
			<tr><td><a href="showImportmodule.php?id=1">PI-01</a></td><td>Software Architecture</td><td>6</td></tr>
			<tr><td colspan="3">8.3.2.1: Bereich A: Daten, intelligente Systeme, Kontexte</td></tr>
			<tr><td><a href="showModule.php?id=4">IDG-07</a></td><td>Data Ethics</td><td>6</td></tr>
			<tr><td colspan="3">9.: Masterabschlussmodul (30 cp)</td></tr>
			<tr><td><a href="showModule.php?id=5">MA-CS</a></td><td>Masterabschlussmodul</td><td>30</td></tr>
			</table>
			</body></html>
			""";

	@Test
	void readsTheRulesAPlanDependsOn() {
		Plan plan = this.parser.plan(FPO, "https://moduldatenbank.uni-kassel.de/showFPO.php?id=120");

		assertThat(plan.rules()).isEqualTo(new ProgrammeParser.Rules(120, 4, 30, 60, 24));
	}

	@Test
	void readsAreasWithTheirCreditsAndModules() {
		Plan plan = this.parser.plan(FPO, "https://moduldatenbank.uni-kassel.de/showFPO.php?id=120");

		assertThat(plan.groups()).extracting(Group::code).containsExactly("1", "8.1", "8.1.1", "8.3.2.1", "9");
		assertThat(plan.groups().getFirst()).isEqualTo(new Group("1", "Wahlpflicht Praktische Informatik", 6,
				java.util.List.of("PI-01", "PI-02")));
		assertThat(plan.groups().get(1).title()).isEqualTo("Schwerpunkt Computational Intelligence & Data Analytics (CIDA)");
		assertThat(plan.groups().get(2).modules()).containsExactly("CIDA-01", "PI-01");
		assertThat(plan.groups().get(3).credits()).isNull();
		// A module listed in several areas is one module.
		assertThat(plan.modules()).containsOnlyKeys("PI-01", "PI-02", "CIDA-01", "IDG-07", "MA-CS");
		assertThat(plan.modules().get("PI-01").url()).isEqualTo("https://moduldatenbank.uni-kassel.de/showImportmodule.php?id=1");
	}

	@Test
	void readsAModuleDescription() {
		String html = """
				<table>
				<tr><td>Modulnummer / Modulcode</td><td>M/E-01</td></tr>
				<tr><td>Modulname</td><td>Discrete Event Systems and Control Theory</td></tr>
				<tr><td>Dauer des Moduls</td><td>Ein Semester</td></tr>
				<tr><td>Häufigkeit des Angebotes</td><td>jährlich im Sommersemester</td></tr>
				<tr><td>Sprache</td><td>Englisch</td></tr>
				<tr><td>Voraussetzungen für die Teilnahme am Modul</td><td>keine</td></tr>
				<tr><td>Prüfungsleistungen</td><td>Klausur (90 Min.) oder mündliche Prüfung (30 Min.)</td></tr>
				<tr><td>Anzahl Credits (ECTS)</td><td>6 cp</td></tr>
				</table>
				""";

		Module m = this.parser.module(html, new ModuleRef("M/E-01", "Discrete Event Systems", 6, "https://example.org/m"));

		assertThat(m.title()).isEqualTo("Discrete Event Systems and Control Theory");
		assertThat(m.offered()).isEqualTo(Offered.SUMMER);
		assertThat(m.oralExam()).isTrue();
		assertThat(m.credits()).isEqualTo(6);
		assertThat(m.durationSemesters()).isEqualTo(1);
		assertThat(m.language()).isEqualTo("Englisch");
	}

	@Test
	void readsWhenModulesAreOffered() {
		assertThat(ProgrammeParser.offered("jährlich im Wintersemester")).isEqualTo(Offered.WINTER);
		assertThat(ProgrammeParser.offered("jedes Semester")).isEqualTo(Offered.BOTH);
		assertThat(ProgrammeParser.offered("im Winter- und Sommersemester")).isEqualTo(Offered.BOTH);
		assertThat(ProgrammeParser.offered("unregelmäßig")).isEqualTo(Offered.UNKNOWN);
	}

}
