package de.unikassel.assistant.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adds the German words the university's pages use to an English question, so keyword search finds
 * them: "semester contribution" → "Semesterbeitrag". Most pages exist only in German.
 */
final class QueryExpander {

	/** English phrase (regex) → German terms. Only common study-organisation topics. */
	private static final Map<String, String> GLOSSARY = Map.ofEntries(
			Map.entry("semester (?:contribution|fee)s?|tuition", "Semesterbeitrag Beitrag Gebühren"),
			Map.entry("re-?regist(?:er|ration)|renew (?:my )?enrol?ment", "Rückmeldung zurückmelden"),
			Map.entry("(?:register|registration|sign up|signing up) (?:for )?(?:an |the |my )?exams?|exam registration", "Prüfungsanmeldung Anmeldung Prüfung"),
			Map.entry("(?:deregister|withdraw|cancel)\\w* (?:from )?(?:an |the |my )?exams?", "Abmeldung Rücktritt Prüfung"),
			Map.entry("(?:sign up|register|registration|enrol)\\w* (?:for )?(?:courses?|classes|lectures|modules?)", "Belegung Anmeldung Lehrveranstaltungen eCampus"),
			Map.entry("timetable|schedule", "Stundenplan Vorlesungsverzeichnis"),
			Map.entry("course catalog(?:ue)?", "Vorlesungsverzeichnis"),
			Map.entry("exam(?:ination)? office", "Prüfungsamt Prüfungsbüro"),
			Map.entry("exam(?:ination)? regulations?|study regulations?", "Prüfungsordnung Studienordnung"),
			Map.entry("module (?:handbook|catalog(?:ue)?|description)s?", "Modulhandbuch Modulbeschreibung"),
			Map.entry("(?:master'?s? )?thesis", "Masterarbeit Abschlussarbeit"),
			Map.entry("leave of absence|take a semester off", "Beurlaubung Urlaubssemester"),
			Map.entry("enrol?l?ment|enrol?l\\b|matriculat", "Immatrikulation Einschreibung"),
			Map.entry("exmatricul|drop out|leave the university", "Exmatrikulation"),
			Map.entry("student (?:id|card)|campus ?card", "CampusCard Studierendenausweis"),
			Map.entry("semester ticket|public transport|bus ticket", "Semesterticket"),
			Map.entry("library", "Bibliothek"),
			Map.entry("scholarships?|funding", "Stipendium Förderung"),
			Map.entry("residence permit|visa", "Aufenthaltstitel Aufenthaltserlaubnis Visum"),
			Map.entry("health insurance", "Krankenversicherung"),
			Map.entry("housing|dorm(?:itory)?|accommodation|halls of residence", "Wohnheim Wohnen Studierendenwerk"),
			Map.entry("canteen|mensa|cafeteria", "Mensa"),
			Map.entry("lecture period|when do lectures (?:start|end)|semester dates", "Vorlesungszeit Semesterzeiten"),
			Map.entry("(?:lecture-?free|semester break|holidays)", "vorlesungsfreie Zeit"),
			Map.entry("credits?|ects", "Leistungspunkte Credits"),
			Map.entry("re-?take|repeat (?:an |the )?exam|fail(?:ed)? (?:an |the )?exam", "Wiederholung Wiederholungsprüfung"),
			Map.entry("grades?|marks", "Note Noten"),
			Map.entry("transcript", "Leistungsübersicht Notenspiegel"),
			Map.entry("recogni[sz]\\w*|credit transfer|transfer credits", "Anerkennung"),
			Map.entry("internship", "Praktikum"),
			Map.entry("part-?time", "Teilzeitstudium"),
			Map.entry("advis(?:or|ory)|counsel(?:l)?ing|guidance", "Studienberatung Beratung"),
			Map.entry("language course|german course", "Sprachkurs Deutschkurs Sprachenzentrum"),
			Map.entry("contact person|who (?:do i|should i) (?:ask|contact)", "Ansprechperson Ansprechpartner Kontakt"),
			Map.entry("office hours?", "Sprechstunde"),
			Map.entry("deadlines?", "Frist Fristen"),
			Map.entry("admission|apply|application", "Zulassung Bewerbung"),
			Map.entry("certificate|diploma", "Zeugnis Urkunde"),
			Map.entry("uni ?account|user ?id|password", "UniAccount Benutzerkonto"),
			Map.entry("wi-?fi|wlan|internet on campus", "WLAN eduroam"),
			Map.entry("e-?mail", "E-Mail Postfach"),
			Map.entry("mentor(?:ing)?", "Mentoring Mentorin Mentor"),
			Map.entry("orientation|introduct(?:ion|ory) (?:week|event)s?|first[- ]year", "Einführung Orientierungswoche Studienstart Erstsemester"));

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final List<Map.Entry<Pattern, String>> RULES = GLOSSARY.entrySet()
		.stream()
		.map((e) -> Map.entry(Pattern.compile("\\b(?:" + e.getKey() + ")", FLAGS), e.getValue()))
		.toList();

	private QueryExpander() {
	}

	/** German terms for the topics the question mentions; empty if none matched. */
	static String germanTerms(String question) {
		Set<String> terms = new LinkedHashSet<>();
		String q = question.toLowerCase(Locale.ROOT);
		for (Map.Entry<Pattern, String> rule : RULES) {
			if (rule.getKey().matcher(q).find()) {
				terms.addAll(List.of(rule.getValue().split(" ")));
			}
		}
		return String.join(" ", terms);
	}

}
