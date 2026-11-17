package de.unikassel.assistant.feed;

import java.time.LocalDate;
import java.util.List;

import de.unikassel.assistant.feed.FeedParser.ParsedItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedParserTests {

	private final FeedParser parser = new FeedParser();

	@Test
	void readsDepartmentEventListing() {
		// Shape copied from /eecs/infothek/termine.html.
		String html = """
				<html><body>
				<nav><ul><li class="news-list-item"><a href="/menu/2026/01/01/x.html">Menu</a></li></ul></nav>
				<main id="inhalt"><ul>
				<li class="news-list-item event-list-item" itemscope itemtype="http://schema.org/Article">
				  <meta itemprop="datePublished" content="2026-10-12" />
				  <div class="media"><p class="card-date"><span class="card-date-day">12.</span>
				  <span class="card-date-month">Oktober 2026</span></p>
				  <div class="media-body event-list-info"><span class="news-meta"><span class="news-cat">ECE</span></span>
				  <h4 class="news-list-title"><a title="ECE Lunch for Newcomer Winter Semester 2026/2027" class="headline-link"
				     href="/eecs/infothek/termine/detailansicht-termin/2026/10/12/ece-lunch.html?cHash=9047">
				     <span itemprop="headline">ECE Lunch for Newcomer Winter Semester 2026/2027</span></a></h4>
				  </div></div>
				</li>
				<li class="news-list-item event-list-item">
				  <meta itemprop="datePublished" content="2027-01-22" />
				  <h4><a class="headline-link" href="/eecs/infothek/termine/detailansicht-termin/2027/01/22/semesterfeier.html">
				  Akademische Semesterfeier</a></h4>
				</li>
				</ul></main>
				</body></html>
				""";

		List<ParsedItem> items = this.parser.parse(html, "https://www.uni-kassel.de/eecs/infothek/termine.html");

		assertThat(items).containsExactly(
				new ParsedItem("ECE Lunch for Newcomer Winter Semester 2026/2027",
						"https://www.uni-kassel.de/eecs/infothek/termine/detailansicht-termin/2026/10/12/ece-lunch.html?cHash=9047",
						LocalDate.of(2026, 10, 12), "ECE"),
				new ParsedItem("Akademische Semesterfeier",
						"https://www.uni-kassel.de/eecs/infothek/termine/detailansicht-termin/2027/01/22/semesterfeier.html",
						LocalDate.of(2027, 1, 22), null));
	}

	@Test
	void fallsBackToDateInUrlAndSkipsUndatedItems() {
		String html = """
				<main id="inhalt">
				<li class="news-list-item"><a class="headline-link" title="Erasmus+ Days"
				   href="/uni/studium/im-studium/veranstaltungskalender/termin/2026/10/15/erasmus.html">Erasmus+ Days</a></li>
				<li class="news-list-item"><a class="headline-link" href="/uni/grundordnung.html">No date anywhere</a></li>
				<li class="news-list-item"><a class="headline-link" href="/uni/x/2026/02/30/bad.html">Impossible date</a></li>
				</main>
				""";

		List<ParsedItem> items = this.parser.parse(html, "https://www.uni-kassel.de/uni/");

		assertThat(items).extracting(ParsedItem::title).containsExactly("Erasmus+ Days");
		assertThat(items.getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 15));
	}

	@Test
	void keepsFirstOccurrenceOfARepeatedLink() {
		String html = """
				<main id="inhalt">
				<li class="news-list-item"><meta itemprop="datePublished" content="2026-10-08" />
				  <a class="headline-link" href="/eecs/infothek/aktuelles/detail/2026/10/8/stromnetze.html">Stromnetze</a></li>
				<li class="news-list-item"><meta itemprop="datePublished" content="2026-10-08" />
				  <a class="headline-link" href="/eecs/infothek/aktuelles/detail/2026/10/8/stromnetze.html">Stromnetze (teaser)</a></li>
				</main>
				""";

		assertThat(this.parser.parse(html, "https://www.uni-kassel.de/eecs/")).extracting(ParsedItem::title)
			.containsExactly("Stromnetze");
	}

}
