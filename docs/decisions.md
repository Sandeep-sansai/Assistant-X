# Decisions

Short records of choices that shape the project, newest last. Add one whenever a choice would be hard to reverse or would surprise someone reading the code.

## 1. Web app first, no native app

A website works on every device, can be shared as a link or QR code, and gets fixes out immediately. It will become an installable PWA later. Native apps only make sense once there are real users and a need (e.g. iOS push).

## 2. Spring Boot + PostgreSQL, Claude through the official Java SDK

This is the stack I already know. The Anthropic Java SDK (`com.anthropic:anthropic-java`) is used directly instead of a framework abstraction so that new API features (effort, fallbacks, citations) are available as soon as they ship.

## 3. Public information only; no scraping behind the login

Grades, registrations, and anything else behind the university's SSO are out of scope. Scraping behind a login is fragile, likely against the IT terms of use, and would mean handling other students' credentials. For personalised features, the plan is to approach the university (ITS, Fachbereich 16, Fachschaft, AStA) with a working demo. Until then, the assistant explains processes and links to the right page.

## 4. v1 retrieval is keyword full-text search, not vectors

PostgreSQL full-text search (German and English stemmers, OR query ranked by `ts_rank_cd`) needs no embedding provider and no extra infrastructure, and it works well for the term-heavy questions students ask ("Rückmeldung", "Prüfungsordnung § 7", "ECTS Masterarbeit").

Known weaknesses: synonyms and paraphrases ("re-register" vs. "Rückmeldung"), and German compounds ("Rückmeldefrist" does not match "Rückmeldung").

Next step: add pgvector embeddings with a multilingual model and combine both rankings (hybrid search with reciprocal rank fusion). Do this only once `eval/questions.csv` has enough questions to show whether it helps.

## 5. The model answers only from retrieved sources and cites them

Wrong deadlines have real consequences. The system prompt forbids general-knowledge answers, requires `[n]` citations, and asks the model to flag conflicting regulation versions. The UI shows each source's retrieval date. If nothing is retrieved, the model isn't called at all, and the student is pointed to the Studierendenservice.

## 6. Anonymous question log

Questions, answers, cited URLs and feedback are stored without user IDs, IPs or sessions. They show what students ask and where the assistant fails, which decides the roadmap. Students may still type personal details into a question, so add a retention limit (e.g. delete after 12 months) before any public launch.

## 7. Refusal fallbacks enabled

Requests carry `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`). If the model declines a request, the API retries it on a fallback model instead of returning nothing. Turn it off with `assistant.claude.fallbacks: false`.

## 8. Student accounts and personalised answers

Students can create an account (university email, verified by a link) to get answers that take their programme, plan and deadlines into account. The anonymous `/api/ask` stays as it is. Personalised questions are stored with the student's id, which changes decision 6 for those rows. Because of that, the 12-month retention limit from decision 6 is now a requirement for all question rows, not a pre-launch to-do.

## 9. Email digest waits for ITS

Reading a student's university mailbox needs an app registration with `Mail.Read` in the university's Microsoft 365 tenant, which most likely needs admin consent, and sends email content to Claude, which needs a data-protection check. The email tasks (Phase 1c in the Kiro spec) are not started until ITS has agreed to both. Everything else in Phase 1 does not depend on them.

## 10. eCampus data only by upload

Grades and enrolments get into the assistant only when the student exports them from eCampus and uploads them. This keeps decision 3: no portal credentials, no scraping, no actions taken inside eCampus. A session-based client is not planned unless the university offers an official API or gives written approval.

## 11. Free local model until the university project is approved

There is no Anthropic API key yet, so development and the pitch demo use a free model that runs locally through Ollama (`qwen2.5:7b`, see `ollama/Modelfile`). Nothing leaves the machine and nothing costs money. The model is reached through any OpenAI-compatible chat completions API, so a hosted service can be plugged in by changing `OPENAI_BASE_URL`, `OPENAI_API_KEY` and `OPENAI_MODEL`.

Claude is still the intended production model (decision 2). Switching back is configuration only: `LLM_PROVIDER=anthropic` plus `ANTHROPIC_API_KEY`. A 7B model follows the "answer only from the sources and cite them" instructions less reliably than Claude, so compare both on `eval/questions.csv` before relying on answer quality.

## 12. Profile picker in the browser before real accounts

The start page is personalised by department, programme and semester, which the student picks on first visit. The choice is stored only in their browser (`localStorage`); the server keeps no profile, and the programme and semester are sent along with each question so the answer fits. This gives personalisation now without storing passwords or personal data. The proper login is the university's SSO, which needs the university's approval; the email-and-password accounts in the Kiro spec (Phase 1b) stay ready as a fallback. Notifications and to-do ticks are also browser-only until there are accounts.

## 13. Key dates are curated, news is read from listing pages

Deadlines and lecture periods come from `academic-calendar.yml`, where every date is checked by hand against its source page and carries the day it was checked. Extracting them automatically would be faster but a wrong deadline does real harm (decision 5). News and events are different: the department and university listing pages share one CMS layout with machine-readable dates, so `FeedService` reads them every 6 hours. A department only gets its own feed once its listing pages are added to `assistant.feed.sources`; five departments use other layouts and still need theirs found.

## 14. University look, but not the university's logo

The start page uses the University of Kassel's web design language so it feels familiar: Roboto, flat white pages with square boxes, magenta `#c7105c` for accents and links, lime boxes for key dates, pale blue boxes for related content and dark grey bars for side headings (values taken from the university's own stylesheet, October 2026). It deliberately does not use the university's logo or wordmark, and it keeps "Unofficial" visible in the header: an unofficial app that looks exactly like a university service would mislead students. If the university adopts the project, swapping in its official branding is a change to the header only.

Roboto is served from `static/fonts/` (Apache 2.0) rather than from Google's servers, because loading Google Fonts from Google has been ruled a GDPR violation by German courts.

## 15. The assistant offers help at the right time ("journeys")

Students give their study start (e.g. WS 2026/27) instead of a semester number, so the semester stays correct every term. From it and the date, the assistant offers guided journeys from `journeys.yml` (first one: "Your first weeks", 45 days before to 6 weeks after the first semester starts) with three choices: do it together (a checklist with links and "ask the assistant" per step), remind me (tomorrow, in 3 days, or as an `.ics` event in the student's own calendar) or not now. Like key dates, every step is checked by hand against its source page. The assistant guides and reminds; it does not act inside eCampus or Moodle, which needs official interfaces (decision 3).

## 16. Answers follow the page language

Small local models drift into the language of the (mostly German) sources. The page sends the chosen language with each question; the prompt asks for that language at the end of the message, and if the answer still comes back in the other language, the server asks the model once more to translate it, keeping the citations.

## 17. One assistant, specialist agents behind it

Students talk to one assistant (the Concierge); specialist agents for deadlines, onboarding, campus life and regulations do the work, each with only the data it needs. Agents only suggest actions (save a reminder, add to calendar, open a checklist); nothing happens until the student taps. Routing is rule-based for now because the local model is unreliable at choosing tools; with Claude the same agents become tools in a tool-use call. Until there are accounts, the browser sends the student's state with each message and keeps what agents produce. Background runs, per-student storage, the inbox agent and the study planner wait for login (see Requirement 14 in the Kiro spec).

## 18. Study plans come from the official regulations, not from eCampus or third-party sites

The study planner reads the programme from the university's module database (`moduldatenbank.uni-kassel.de`): the examination regulations with their study and examination plan (areas, focus options, credits per area, the 60-credit rule for the thesis) and each module's description (when it is taught, exam form, prerequisites). Each import records the source URL and date, and the planner flags modules whose description does not say when they are taught instead of guessing.

eCampus is not crawled: its `robots.txt` disallows all automated access, and it holds students' personal data (decision 3). Third-party planners such as spacedevelopers.net were looked at for ideas (free-day filter, ready-made plans, calendar export) but not used as a data source: they do not name their source or licence and only cover the old Informatik M.Sc. (PO 2018). The university's past module data, once provided as anonymised totals (docs/past-data-request.md), is an optional input that makes plans more realistic.

The module database uses a certificate under "HARICA TLS RSA Root CA 2021", which current JDKs do not ship; the app adds exactly this root (from Apple's system root store, fingerprint checked against the server) to Java's own for its fetcher.

## 19. English means English, even where the university only publishes German

The module database, the regulations and most news pages exist only in German, so a student who chose English saw a mix. The server now keeps English versions of these short texts (module and area names, news headlines and their categories) in the `translation` table:

- Names fixed by the regulations (areas, focus options, the thesis module) come from a reviewed glossary in `Translator`.
- Everything else is translated once by the language model, in small numbered batches, and stored; answers that don't look like a title are discarded.
- Pages never wait for the model: a missing translation is queued and the German text shows until it is ready.

German keeps the official names, including modules the university itself titles in English. Links still lead to the official German pages, and the planner says so.

## 20. Course times from the eCampus course catalogue, with the IT service's permission

Students want to see when their planned modules meet and whether they clash. The weekly times are only published in the eCampus course catalogue (Vorlesungsverzeichnis), and eCampus's `robots.txt` disallows all automated access (decision 18).

On 2026-10-19 the project owner reported that the university's IT service (ITS) gave permission to import the public course catalogue. Ask ITS to confirm it in writing and keep that with the project. The import stays within these limits:

- Only the public catalogue pages, never anything behind a login.
- Only the programmes the planner supports (for now FB 16, Computer Science M.Sc.), and only the current and next semester.
- Slow: a few seconds between page loads, at most once a day, at night.
- The tool identifies itself as this project.
- It runs on the maintainer's machine and sends the result to `POST /api/admin/timetable` (admin token). The server never crawls eCampus itself.

How it reads the times: `tools/ecampus-import/import.mjs` drives a headless Chrome through the public "Tagesaktuelle Veranstaltungen" page. It reads the five days of one lecture week (the third week of the semester), up to 300 rows per page: about 15–20 page loads per semester. The page lists every course of the university; the app keeps only those matching a planner module. Parallel groups ("2. PG") are kept as alternatives.

When it runs: nightly at 03:15 through a launchd job (`~/Library/LaunchAgents/de.unikassel.assistant.timetable.plist` → `tools/ecampus-import/nightly.sh`). It imports the current and the next semester, and skips the run if the app isn't running. The log is at `~/Library/Logs/uni-kassel-assistant-timetable.log`. To stop it: `launchctl bootout gui/$(id -u)/de.unikassel.assistant.timetable`.

Times are stored per semester in `course_slot` and matched to the planner's modules by module code or by course title. For a semester without imported times, the planner uses the latest semester of the same season and marks it as an estimate. Students can also add times by hand; those stay in their browser. Exercise groups of one module are treated as alternatives, so an overlap with one of them is shown as a soft clash.

If ITS withdraws the permission, delete the import tool and the imported rows. Manual times keep working.

## 21. The page is a React app, built into the backend

The page started as one HTML file (CSS, markup and about 2,700 lines of JavaScript). As it grew (chat, planner with three views, timetable), it became hard to change and to explain, so it was rewritten in React with TypeScript (`frontend/`, built with Vite).

- **Same look, same behaviour.** The styles moved over unchanged, and each part of the old page became a component (`Header`, `Hero`, `Chat`, `Planner`, …). Logic without drawing (to-dos, journeys, feed matching, clashes) sits in plain functions in `src/lib/` and `src/planner/timetable.ts`. Before the switch, the React version was checked feature by feature against the old page in a real browser.
- **One app to run.** `npm run build` writes the page into Spring Boot's `static/` folder, and the built files are committed. So the backend runs without Node, and hosting doesn't change. During development, `npm run dev` gives live reload and forwards `/api` to Spring Boot.
- **No extra libraries.** Only React; state is a React context plus localStorage, as before (decision 12). That keeps the bundle small (about 80 KB compressed).

The old single-file page is kept in `docs/legacy/single-file-page.html` for reference until the React version has been in use for a while.
