# Uni Kassel Study Assistant

An unofficial assistant that answers students' questions about studying at Uni Kassel (registration, re-registration, exam regulations, module handbooks, thesis) from the university's public web pages, with links to the sources.

> Student project, not an official university service. Answers can be wrong or out of date. Always check the linked source.

## How it works

```
uni-kassel.de sitemaps ─┐
module database iframes ─┼─> crawler ─> text extraction ─> chunks ─> PostgreSQL (full-text index, DE + EN)
linked PDFs ────────────┘                                                │
                                                                         ▼
question ─> keyword retrieval (top 8 chunks) ─> LLM answers only from those chunks ─> answer + cited links
                                                                         │
                                                       anonymous question log + 👍/👎 feedback
```

- **Crawler** ([`ingest/`](backend/src/main/java/de/unikassel/assistant/ingest)): reads the `/uni` and `/eecs` sitemaps and keeps the URLs under the study-related prefixes in `application.yml`. It also follows iframes embedded in page content (the Prüfungsordnung and Modulhandbuch are embedded from `moduldatenbank.uni-kassel.de`) and links matching `follow-patterns`. It waits 1.5 s between requests, identifies itself with a User-Agent, and re-chunks a page only when its text changes. It runs weekly, or on demand through the admin endpoint.
- **Retrieval** ([`rag/ChunkSearch`](backend/src/main/java/de/unikassel/assistant/rag/ChunkSearch.java)): PostgreSQL full-text search with both the German and English stemmers.
- **Answering** ([`rag/AnswerService`](backend/src/main/java/de/unikassel/assistant/rag/AnswerService.java)): the model (local Ollama by default, Claude in production; see [`llm/`](backend/src/main/java/de/unikassel/assistant/llm)) gets the excerpts and is told to answer only from them, cite them as `[n]`, answer in the question's language, and say when it doesn't know.

## Start page

Besides the chat, the start page shows:

- **What can I do for you?** with suggested questions, led by ones tied to the nearest deadline.
- **Up next**: countdowns to key dates (lecture periods, breaks) from [`academic-calendar.yml`](backend/src/main/resources/academic-calendar.yml). Every date there is checked by hand; add new ones the same way.
- **What's happening**: events and news from the student's department and the university, read every 6 hours from the listing pages in `assistant.feed.sources` ([`feed/`](backend/src/main/java/de/unikassel/assistant/feed)).
- **To do** and a **notification bell**: actions due soon, events today or tomorrow, and news since the last visit. Ticked-off items move to a "Done" list with the day they were done and can be reopened.
- **Agents**: the chat goes through a Concierge that hands each message to a specialist ([`agent/`](backend/src/main/java/de/unikassel/assistant/agent)): Deadlines ("remind me to … on Friday at 10", "what's coming up?"), Onboarding ("what's next on my checklist?"), Campus life ("what's on this week?") and Regulations (everything else, answered from the uni pages with sources). Replies show which agent answered and offer one-tap actions such as saving a reminder.
- **Study planner**: for the Computer Science M.Sc. (PO 2026), imported from the official regulations and module descriptions in the module database ([`programme/`](backend/src/main/java/de/unikassel/assistant/programme), [`planner/`](backend/src/main/java/de/unikassel/assistant/planner)). "Plan it for me" builds a plan for 120 credits in the number of semesters the student wants, for the chosen focus, respecting when modules are taught and the regulations' rules; the student can then move, add, remove or mark modules as passed, and every change is checked. The chat's Planner agent does the same ("plan my master in 5 semesters with the focus CIDA"). Past module data from the university can be added later (docs/past-data-request.md).
- **Voice**: a microphone button in both question boxes (browsers with speech recognition, e.g. Chrome, Edge, Safari). Spoken questions are sent when the student stops talking and their answers are read aloud; every answer also has a "Read aloud" button. In Chrome, speech recognition runs on Google's servers; reading aloud uses the device's own voices.
- **Your assistant**: guided journeys from [`journeys.yml`](backend/src/main/resources/journeys.yml), offered when they fit the student's study start and the date (first one: "Your first weeks" for first-semester students), with "Do it with me", "Remind me" (including an `.ics` calendar event) and "Not now".
- **Feed for you**: the department's own news and events next to the university-wide ones, with items for the student's programme or for new students ranked first. The profile dialog shows a live preview.

The page is in English and German (switch in the header; the default follows the browser language). Texts live in [`frontend/src/i18n/strings.ts`](frontend/src/i18n/strings.ts); German texts for key dates go under `de:` in `academic-calendar.yml`. Every page load starts with a short opening animation that flips a tear-off calendar to today's date; a click or key press skips it, and it is never shown when the system asks for reduced motion.

Students pick their department, programme and semester on first visit. It's saved in their browser only ([decision 12](docs/decisions.md)); real login comes with the university's SSO.

## Run it locally

You need Java 25, PostgreSQL 17 and a language model. By default the model is a free one that runs locally through [Ollama](https://ollama.com); no API key is needed (see [decision 11](docs/decisions.md)).

```bash
# 1. PostgreSQL (one-time)
brew install postgresql@17
brew services start postgresql@17
/opt/homebrew/opt/postgresql@17/bin/createdb assistant

# 1b. Local model (one-time, ~5 GB download)
brew install ollama
brew services start ollama
ollama pull qwen2.5:7b
ollama create qwen2.5-7b-16k -f ollama/Modelfile   # same model with a larger context window

# 2. Configuration
export ADMIN_TOKEN=$(openssl rand -hex 16)     # protects the admin endpoint
export CRAWLER_CONTACT=you@student.uni-kassel.de   # shown in the crawler's User-Agent

# 3. Start (Flyway creates the tables on startup)
cd backend && ./mvnw spring-boot:run

# 4. Fill the knowledge base (takes ~10–20 minutes because of the polite crawl delay)
curl -X POST localhost:8080/api/admin/ingest -H "X-Admin-Token: $ADMIN_TOKEN"

# 5. Open http://localhost:8080 and ask something
```

### The page (React)

The page is a React + TypeScript app in [`frontend/`](frontend/), built with Vite. `npm run build` writes it into `backend/src/main/resources/static/`, so Spring Boot serves it and there is still one app to run. The built files are kept in the repository, so the backend works without Node.

```bash
cd frontend
npm install              # once
npm run dev              # http://localhost:5173 with live reload; /api goes to Spring Boot on :8080
npm run build            # type-check and write the page into the backend's static folder
```

If your Postgres user or password differs from the defaults, set `DB_URL`, `DB_USER`, `DB_PASSWORD`.

### Using Claude instead of the local model

```bash
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-...           # from console.anthropic.com
```

Any other server with an OpenAI-compatible `/chat/completions` endpoint works with `OPENAI_BASE_URL`, `OPENAI_API_KEY` and `OPENAI_MODEL`.

## API

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/ask` | `{"question": "...", "profile": {"department": "fb16", "programme": "...", "semester": 3}, "language": "en"}` | `profile` and `language` are optional; with `language`, the answer is in that language. Returns `{questionId, answer, sources[]}` |
| GET | `/api/university` | | Departments and programmes for the profile picker |
| GET | `/api/calendar?department=fb16` | | Upcoming key dates, nearest first |
| GET | `/api/feed?department=fb16` | | Recent news and upcoming events: the department's, then university-wide ones |
| GET | `/api/journeys?department=fb16&start=WS2026&lang=en` | | Guided journeys that apply now for that study start |
| GET | `/api/programmes` · `/api/programmes/{id}` | | Programmes the planner supports; the full catalogue of one |
| POST | `/api/planner/plan` | `{"programme": "cs-msc-2026", "focus": "8.1", "start": "WS2026", "semesters": 4, "completed": [], "preferred": [], "maxCreditsPerSemester": null}` | A generated plan with requirement progress, warnings and notes |
| POST | `/api/planner/check` | `{"programme", "focus", "start", "semesters": [["PI-11", …], …], "completed": [...]}` | Checks a plan the student edited |
| POST | `/api/admin/programmes/import` | header `X-Admin-Token` | Re-imports the programmes from the module database |
| POST | `/api/assistant` | `{"message": "...", "language": "en", "profile": {...}, "journeyDone": {...}, "reminders": [...]}` | The chat. Returns `{agent, text, cards[], actions[], sources[], questionId}` |
| POST | `/api/questions/{id}/feedback` | `{"rating": 1 \| -1, "comment": "..."}` | |
| POST | `/api/admin/ingest` | header `X-Admin-Token` | Starts a crawl in the background |
| POST | `/api/admin/feed/refresh` | header `X-Admin-Token` | Re-reads the news and event pages now |

## Configuration

Everything is in [`application.yml`](backend/src/main/resources/application.yml). The most useful settings:

- `LLM_PROVIDER`: `openai-compatible` (default, local Ollama) or `anthropic`.
- `CLAUDE_MODEL` (default `claude-opus-5-5`) and `CLAUDE_EFFORT` (default `medium`), used with `LLM_PROVIDER=anthropic`. For a cheaper setup, try `claude-haiku-5-5` and measure it against `eval/questions.csv` first.
- `assistant.crawler.include-prefixes`: which parts of the site get crawled. Add other programs here.
- `CRAWLER_CRON`: crawl schedule. Set it to `-` to turn the schedule off.
- `assistant.feed.sources`: news and event listing pages per department. `FEED_CRON` sets how often they're read (default every 6 hours).
- `assistant.planner.programmes`: programmes the study planner supports (regulations URL in the module database). `PLANNER_HISTORY_FILE` points to the university's past module data, once available.
- `assistant.university.departments`: departments and programme suggestions for the profile picker.

## Project layout

```
backend/   Spring Boot service (crawler, retrieval, answering, API); serves the built page
frontend/  React + TypeScript page (start page, chat, study planner), built with Vite
tools/     eCampus timetable import (runs on the maintainer's machine, see decision 20)
ollama/    Modelfile for the free local model
docs/      Decisions and roadmap
eval/      Test questions with expected answers, used to measure changes
```

### Timetable (planner week view)

- `GET /api/timetable?programme=cs-msc-2026&terms=WS2026,SS2027`: imported course times and which semesters have any.
- `POST /api/admin/timetable` (header `X-Admin-Token`): replaces one semester's times, e.g.
  `{"programme":"cs-msc-2026","term":"WS2026","courses":[{"title":"Deep Learning","url":"…","slots":[{"kind":"Vorlesung","weekday":1,"start":"10:00","end":"12:00","room":"…"}]}]}`.
  Course titles are matched to modules automatically; the response says how many matched. See docs/decisions.md #20.
