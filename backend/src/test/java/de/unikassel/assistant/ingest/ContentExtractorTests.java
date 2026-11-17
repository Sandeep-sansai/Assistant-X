package de.unikassel.assistant.ingest;

import de.unikassel.assistant.ingest.ContentExtractor.ExtractedPage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTests {

	private final ContentExtractor extractor = new ContentExtractor();

	@Test
	void extractsMainContentWithHeadingPaths() {
		String html = """
				<html><head><title>Rückmeldung - Universität Kassel</title></head><body>
				<nav id="hauptnavigation"><a href="/x">Menu</a></nav>
				<main id="inhalt">
				  <h1>Rückmeldung</h1>
				  <p>Sie müssen sich jedes Semester zurückmelden.</p>
				  <h2>Fristen</h2>
				  <ul><li>Wintersemester: <b>bis 15. Juli</b><ul><li>nested</li></ul></li></ul>
				  <table><tr><th>Beitrag</th><td>300 EUR</td></tr></table>
				</main>
				<footer>Impressum</footer>
				</body></html>
				""";

		ExtractedPage page = this.extractor.html(html, "https://www.uni-kassel.de/uni/studium/im-studium/rueckmeldung.html");

		assertThat(page.title()).isEqualTo("Rückmeldung");
		assertThat(page.sections()).hasSize(2);
		assertThat(page.sections().get(0).heading()).isEqualTo("Rückmeldung");
		assertThat(page.sections().get(0).paragraphs()).containsExactly("Sie müssen sich jedes Semester zurückmelden.");
		assertThat(page.sections().get(1).heading()).isEqualTo("Rückmeldung > Fristen");
		assertThat(page.sections().get(1).paragraphs()).containsExactly("Wintersemester: bis 15. Juli nested",
				"Beitrag | 300 EUR");
		assertThat(page.fullText()).doesNotContain("Menu", "Impressum");
	}

	@Test
	void collectsScriptEmbedsAndLinks() {
		String html = """
				<html><body><main id="inhalt">
				  <script>embedIFrameResponsive.embed('https://moduldatenbank.uni-kassel.de/showFPO.php?id=120', s);</script>
				  <p>See the <a href="/uni/doc.pdf#page=2">regulation</a>.</p>
				</main></body></html>
				""";

		ExtractedPage page = this.extractor.html(html, "https://www.uni-kassel.de/uni/studium/x.html");

		assertThat(page.embeds()).containsExactly("https://moduldatenbank.uni-kassel.de/showFPO.php?id=120");
		assertThat(page.links()).containsExactly("https://www.uni-kassel.de/uni/doc.pdf");
		assertThat(page.fullText()).doesNotContain("embedIFrameResponsive");
	}

	@Test
	void treatsRegulationParagraphsAsHeadings() {
		String html = """
				<html><body>
				  <p><b>§ 7 Prüfungsleistungen, Modulprüfungen, Wiederholungen</b></p>
				  <p>Nicht bestandene Prüfungen können zweimal wiederholt werden.</p>
				</body></html>
				""";

		ExtractedPage page = this.extractor.html(html, "https://moduldatenbank.uni-kassel.de/showFPO.php?id=120");

		assertThat(page.sections()).singleElement().satisfies((section) -> {
			assertThat(section.heading()).isEqualTo("§ 7 Prüfungsleistungen, Modulprüfungen, Wiederholungen");
			assertThat(section.paragraphs()).hasSize(1);
		});
	}

}
