# Implementation Plan: Personalized University Student Assistant

## Overview

Extends the stateless RAG-based Q&A bot into an identity-aware academic companion across three
incremental phases. Phase 1 delivers authentication, profiles, personalized Q&A, and email
digests. Phase 2 adds the eCampus upload bridge and the semester course planner. Phase 3 adds
wellness reminders and campus event suggestions.

All new code follows existing conventions: Spring-managed beans, `JdbcClient` for DB access, and
the Anthropic Claude SDK for AI synthesis.

---

## Tasks

### Phase 0 — Start page (built 2026-10-11)

- [x] P0.1 `LlmClient` interface with Claude and OpenAI-compatible (local Ollama) implementations
- [x] P0.2 `AnswerService.answer(question, studentContext)`; `/api/ask` accepts an optional profile
  (first half of task 0.1; the `studentId` parameter follows with accounts)
- [x] P0.3 `UniversityProperties` with departments and programmes (first part of task 2.1)
- [x] P0.4 Feed: `FeedParser`, `FeedStore`, `FeedService`, migration `V2__feed.sql`, `GET /api/feed`
- [x] P0.5 Curated academic calendar: `academic-calendar.yml`, `CalendarService`, `GET /api/calendar`
- [x] P0.6 Start page: profile picker, suggestions, key dates, feed, to-dos, notifications, chat panel
- [x] P0.10 Answers in the page language (prompt + translation safety net, `LanguageGuard`)
- [x] P0.11 Study start instead of semester number; feed split into "For you" and "Across the university" with ranking and live preview
- [x] P0.12 Journeys (`journeys.yml`, `JourneyService`, `GET /api/journeys`): "Your first weeks" with do-it-together checklist, reminders and `.ics` export
- [x] P0.13 Opening animation flips from the semester start to today and shows the next key date
- [ ] P0.14 More journeys: Rückmeldung, exam registration, thesis registration (each step checked against its source)
- [ ] P0.7 Find the news/event listing pages of FB 05, 06, 10, 14, 15 and add them to `assistant.feed.sources`
- [ ] P0.8 Add Rückmeldung SoSe 2027 and per-department exam registration periods to the calendar once published
- [ ] P0.9 Improve retrieval and stop invented links (see "Known issues" in Notes) before a public demo

`V2` is taken by the feed migration, so the student-profile migration in task 3 becomes `V3`, and
the later ones shift by one.

### Phase 1 — Auth, Profile, Personalized Q&A, Email Digest

Phase 1 is split in three parts: **1a** (tasks 0) refactors existing code, **1b** (tasks 1–3,
5–11, 14–16) adds accounts and personalized Q&A, and **1c** (tasks 4, 12, 13) adds email. Do not
start 1c until ITS has approved the `Mail.Read` app registration and the data-protection
questions are settled (`docs/decisions.md` #9).

- [ ] 0. Prerequisite refactor of the existing RAG code
  - [ ] 0.1 Add `AnswerService.answer(String question, String studentContext, Long studentId)`
    - The existing `answer(String)` delegates with `null, null`; `/api/ask` behaviour unchanged
    - When `studentContext` is non-null, put it before `<sources>` in the user message and append
      the personalization sentence from the design to the system prompt
    - `QuestionLog.record(...)` takes a nullable `studentId` and writes `question.student_id`
      (column added in task 3); the question is logged once, inside `AnswerService`
    - _Requirements: 3.3, 3.6, 3.7_
  - [ ] 0.2 Make `ChunkSearch`, `ChunkSearch.search()` and `ChunkSearch.RetrievedChunk` public
    - Needed by `PlannerService` (task 20) and the event job (task 26) in other packages
    - _Requirements: 9.2, 11.1_
  - [ ]* 0.3 Add an `AnswerService` unit test with a mocked `AnthropicClient` and `ChunkSearch`
    - Verify the context block is placed in the request only when provided, and that no Claude
      call is made when retrieval returns nothing
    - _Requirements: 3.3, 3.7_

- [ ] 1. Add Phase 1 dependencies to `pom.xml`
  - Add `com.nimbusds:nimbus-jose-jwt:9.47` for RS256 JWT creation and verification
  - Add `net.jqwik:jqwik:1.9.2` (test scope) for property-based testing
  - Add `com.github.ben-manes.caffeine:caffeine:3.2.0` for in-memory context cache
  - Add `org.springframework.boot:spring-boot-starter-security` (managed version) for the HTTP
    filter chain and BCrypt — together with task 7.6, or every endpoint gets locked
  - Add `org.springframework.boot:spring-boot-starter-mail` (managed version) for verification links
  - Add `org.testcontainers:postgresql` (managed version, test scope) for integration test DB
    (needs a local Docker runtime)
  - `com.microsoft.graph:microsoft-graph` is added later, in task 12 (Phase 1c)
  - Check each version against Maven Central before adding it
  - _Requirements: 1, 12, 13_

- [ ] 2. Implement `UniversityProperties` configuration record
  - [ ] 2.1 Create `UniversityProperties` record in `config` package
    - Map all `assistant.university.*` YAML keys: `name`, `short-name`, `email-domain-pattern`,
      `locale`, `grade-scale`, `term-format`, `portal-type`, `portal-base-url`, `sso-type`,
      `sso-issuer-url`, `event-search-terms`
    - Annotate with `@ConfigurationProperties("assistant.university")` and `@Validated`
    - Add `@NotBlank` / `@NotNull` constraints on every required field so the app fails to start
      if a key is absent
    - Define `PortalType` enum (`ECAMPUS_UPLOAD`, `ECAMPUS_SESSION`, `CUSTOM_REST`) and
      `GradeScale` enum (`GERMAN_1_TO_5`, `US_LETTER`, `PERCENTAGE`, `CUSTOM`)
    - Extend `AssistantProperties` or register as a separate `@EnableConfigurationProperties` bean
    - Add `assistant.university.*` entries to `application.yml` (Uni Kassel defaults as per design)
    - _Requirements: 12.1, 12.2, 12.4, 12.5, 12.6, 12.7, 12.8_

  - [ ]* 2.2 Write unit tests for `UniversityProperties` validation
    - Verify app context fails to start when a required key is missing
    - Verify invalid enum values for `portal-type` and `grade-scale` are rejected at startup
    - _Requirements: 12.6, 12.7, 12.8_

- [ ] 3. Write Flyway migration V3 — student, preferences, verification, question link
  - Create `V3__student_profile.sql` in `src/main/resources/db/migration`
  - Define `student` table (id, email UNIQUE, password_hash, email_verified_at, display_name,
    degree_program, enrollment_year, target_semester, created_at, last_seen_at) exactly as in
    design
  - Define `email_verification` table (token_hash PK, student_id FK, expires_at)
  - Define `student_preference` table (id, student_id FK, name, value, updated_at) with
    `UNIQUE(student_id, name)` and index `student_pref_student_idx`
  - `ALTER TABLE question ADD COLUMN student_id … ON DELETE SET NULL` plus the partial index
  - _Requirements: 1.12, 2.1, 2.3, 3.6_

- [ ] 4. *(Phase 1c — after ITS approval)* Write Flyway migration (next free version) — connected accounts table
  - Give the file the next free version number at the time it is written (see the numbering
    note in the design's "New Schema" section); "V3" is only the design's working name
  - Create `V<next>__connected_accounts.sql`
  - Define `connected_account` table (id, student_id FK, provider, provider_email,
    token_ciphertext BYTEA, token_iv BYTEA, token_tag BYTEA, scope, expires_at, connected_at,
    last_used_at) with `UNIQUE(student_id, provider)` exactly as in design
  - _Requirements: 4.3_

- [ ] 5. Write Flyway migration V4 — notifications and email digests tables
  - Create `V4__notifications.sql`
  - Define `notification_log` table (id, student_id FK, type, title, body, read, created_at)
    with index on `(student_id, created_at DESC)`
  - Define `email_digest` table (id, notification_id FK, email_count, earliest_email_at,
    latest_email_at, raw_subjects TEXT[]) exactly as in design
  - _Requirements: 5.6, 6.1_

- [ ] 6. Implement `ProfileService` and student data access
  - [ ] 6.1 Define `StudentProfile` record and `ProfileService` interface
    - Create `StudentProfile` record (studentId, email, displayName, degreeProgram,
      enrollmentYear, targetSemester, createdAt, lastSeenAt) in a new `profile` package
    - Create `ProfileService` interface matching the design signature
    - _Requirements: 2.1, 2.2_

  - [ ] 6.2 Implement `ProfileServiceImpl` using `JdbcClient`
    - Implement `upsert()`: INSERT … ON CONFLICT DO UPDATE for `student` row
    - Implement `get()`: SELECT by id; throw `StudentNotFoundException` (→ 404) if absent
    - Implement `preferences()`: SELECT all from `student_preference` for the given student
    - Implement `setPreference()`: INSERT … ON CONFLICT DO UPDATE on `(student_id, name)`
    - Update `last_seen_at` on every `get()` call (_Requirement 2.6_)
    - _Requirements: 2.2, 2.3, 2.4, 2.5_

  - [ ]* 6.3 Write unit tests for `ProfileServiceImpl`
    - Mock `JdbcClient`; verify upsert SQL, preference upsert idempotency, `last_seen_at` update
    - _Requirements: 2.4, 2.6_

- [ ] 7. Implement JWT auth — `JwtService`, `AuthFilter`, and auth endpoints
  - [ ] 7.1 Implement `JwtService` (RS256 sign + verify using Nimbus)
    - Load private key from `JWT_PRIVATE_KEY_PATH` env var at startup; fail FATAL if absent
    - `issue(studentId)` → signed RS256 JWT, 24-hour expiry, `sub` = studentId
    - `verify(token)` → `DecodedClaims` or throw typed exceptions (missing, malformed, expired)
    - `refresh(token)` → new token if expired ≤ 7 days ago; throw `TokenExpiredException`
      beyond window (_Requirement 1.9, 1.10_)
    - _Requirements: 1.8, 1.9, 1.10, 1.11_

  - [ ] 7.2 Implement `AuthController` (`POST /api/auth/register`, `POST /api/auth/login`,
         `POST /api/auth/refresh`, `GET /api/auth/verify`)
    - `register`: validate email against `UniversityProperties.emailDomainPattern`, hash password
      with bcrypt cost 12, create student row via `ProfileService`, return 201 + JWT +
      `studentId`; return 409 on duplicate email; return 422 on domain mismatch or missing fields
    - `register` also creates an `email_verification` row (store SHA-256 of a random token,
      24 h expiry) and sends the link via `VerificationMailer`; the `dev` profile implementation
      logs the link instead of sending mail
    - `verify`: consume the token, set `email_verified_at`, redirect to the frontend
    - `login`: load student by email, verify bcrypt hash, return 200 + JWT + `studentId`;
      return 401 for unknown email or wrong password (same `invalid_credentials` code for both);
      return 403 `email_not_verified` if `email_verified_at` is NULL
    - `refresh`: delegate to `JwtService.refresh()`; return 200 + new JWT or 401 with
      `token_expired`
    - Never log, return, or store plain-text passwords
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.12, 1.13, 13.1_

  - [ ] 7.3 Implement `AuthFilter` (Spring Security `OncePerRequestFilter`)
    - Guard all `/api/me/**` paths only. `/api/oauth/callback/*` must stay public: it is a
      browser redirect from Microsoft and carries no JWT; identity comes from the signed state
    - Extract Bearer token → call `JwtService.verify()` → store `studentId` in
      `SecurityContextHolder` / request attribute
    - Return 401 with `missing_token` or `invalid_token` without leaking stack traces
    - _Requirements: 1.11, 13.5_

  - [ ] 7.6 Implement the `SecurityFilterChain` bean
    - Public: `/`, static resources, `/actuator/health`, `POST /api/ask`,
      `POST /api/questions/*/feedback`, `/api/auth/**`, `GET /api/oauth/callback/*`,
      `/api/admin/**` (keeps its own `X-Admin-Token` check); JWT: `/api/me/**`; deny the rest
    - Stateless sessions, CSRF off (no auth cookies), form login and HTTP Basic off
    - Add a `@WebMvcTest` that `/api/ask` and `/` still work without a token and `/api/me` returns
      401 without one
    - _Requirements: 1.14, 3.7_

  - [ ]* 7.4 Write unit tests for `JwtService`
    - Test sign → verify round-trip, expiry boundary (24 h), refresh window (7 days),
      tampered-token rejection, missing key at startup
    - _Requirements: 1.8, 1.9, 1.10_

  - [ ]* 7.5 Write property test for OAuth state TTL and tamper detection (Property 4)
    - Use jqwik: for arbitrary `studentId` and 16-byte nonce, assert `validate(sign(...), now +
      9 min) = true`, `validate(..., now + 11 min) = false`, and every single-byte mutation
      of the state is rejected
    - **Property 4: OAuth state TTL and tamper detection**
    - **Validates: Requirements 4.4**

- [ ] 8. Implement `ProfileController` — profile and preference endpoints
  - [ ] 8.1 Implement `GET /api/me` and `PUT /api/me`
    - `GET`: return `StudentProfileDto` from `ProfileService.get()`
    - `PUT`: validate fields (displayName ≤ 100 chars, degreeProgram ≤ 200, enrollmentYear
      1900–2100, targetSemester 1–20), ignore read-only fields (email, studentId), call
      `ProfileService.upsert()`; return 422 with `invalid_profile_data` if no valid field
      present
    - _Requirements: 2.1, 2.2_

  - [ ] 8.2 Implement `GET /api/me/preferences` and `PUT /api/me/preferences/{name}`
    - `GET`: return map of all preferences for the authenticated student
    - `PUT`: validate `{name}` ≤ 100 chars and `value` ≤ 500 chars; call
      `ProfileService.setPreference()`; return 204 or 422 with `preference_value_too_long`
    - _Requirements: 2.3, 2.4_

  - [ ]* 8.3 Write property test for student profile round-trip (Property 9)
    - Use jqwik: for arbitrary valid `StudentProfileDto`, assert `PUT /api/me` then
      `GET /api/me` returns equal field values
    - **Property 9: Student profile round-trip**
    - **Validates: Requirements 2.1, 2.2**

  - [ ]* 8.4 Write property test for preference upsert idempotency (Property 10)
    - Use jqwik: for arbitrary preference `name` and sequence of `values`, assert
      `GET /api/me/preferences` returns exactly the last written value
    - **Property 10: Preference upsert idempotency**
    - **Validates: Requirements 2.4**

- [ ] 9. Checkpoint — Phase 1 foundations
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement `StudentContextBuilder` with token-budget enforcement
  - [ ] 10.1 Implement `StudentContextBuilderImpl`
    - Pull profile, preferences, and DEADLINE notifications from the last 14 days from
      `notification_log`; planner and completed-course sections are added in task 20.3 (Phase 2),
      because their tables only exist from the course-planner migration (task 17)
    - Render `<student-context>` XML block as described in the design's "Personalization
      Strategy" section
    - Enforce `assistant.personalization.context-max-tokens` × 4 char budget; drop in order:
      completed modules older than 2 years → preferences → plan slots; always retain DEADLINEs
    - Cache rendered string per `studentId` for 5 minutes using Caffeine `LoadingCache`
    - Add `assistant.personalization.context-max-tokens` config key to
      `AssistantProperties` (default 2000)
    - _Requirements: 3.2, 3.4, 3.5_

  - [ ]* 10.2 Write property test for context token budget enforcement (Property 1)
    - Use jqwik: for arbitrary profile, large preference maps, and plan lists, assert
      `buildContext().length() ≤ contextMaxTokens × 4` always holds
    - **Property 1: Personalization context token budget enforcement**
    - **Validates: Requirements 3.4**

- [ ] 11. Implement `PersonalizedAnswerService` and `POST /api/me/ask`
  - [ ] 11.1 Implement `PersonalizedAnswerServiceImpl`
    - Call `StudentContextBuilder.buildContext(studentId)` to get context block
    - Delegate to `AnswerService.answer(question, contextBlock, studentId)` (added in task 0.1);
      `AnswerService` adds the context, the personalization sentence, and logs the question with
      `student_id` — this service does not log anything itself
    - Fall back to a `null` context and log WARN if `StudentContextBuilder` throws
    - _Requirements: 3.1, 3.3, 3.6, 3.7, 3.9, 3.10_

  - [ ] 11.2 Implement `PersonalizedAskController` (`POST /api/me/ask`)
    - Extract `studentId` from `SecurityContextHolder`
    - Validate question ≤ 1 000 chars; return 422 with `question_too_long` if exceeded
    - Delegate to `PersonalizedAnswerService.answer()`; return same `Answer` DTO shape as
      `/api/ask`
    - _Requirements: 3.1, 3.8_

  - [ ]* 11.3 Write unit tests for `PersonalizedAnswerService`
    - Mock `StudentContextBuilder` and `AnswerService`; verify context is prepended to system
      prompt; verify fallback on context builder error
    - _Requirements: 3.3, 3.10_

- [ ] 12. *(Phase 1c — after ITS approval)* Implement OAuth token store and `EmailIntegrationService`
  - Add `com.microsoft.graph:microsoft-graph` to `pom.xml` as the first step
  - [ ] 12.1 Implement `OAuthTokenStore` with AES-256-GCM encryption
    - Load encryption key from `ACCOUNT_ENCRYPTION_KEY` env var; fail FATAL at startup if
      absent (_Requirement 13.4_)
    - `save(studentId, provider, tokens)`: encrypt tokens JSON with AES-256-GCM, store
      ciphertext, IV, and tag in `connected_account`
    - `load(studentId, provider)`: decrypt and return tokens; throw `TokenDecryptionException`
      on key mismatch or bit-flip
    - `delete(studentId, provider)`: remove row
    - _Requirements: 4.3, 13.2, 13.4_

  - [ ]* 12.2 Write property test for AES-256-GCM round-trip and integrity (Property 7)
    - Use jqwik: for arbitrary `OAuthTokens` and 32-byte key, assert decrypt(encrypt(tokens,
      key), key) = tokens; wrong key throws `AEADBadTagException`; single bit-flip in
      ciphertext throws `AEADBadTagException`
    - **Property 7: OAuth token AES-256-GCM round-trip and integrity**
    - **Validates: Requirements 4.3**

  - [ ] 12.3 Implement `OAuthStateFactory` and `OAuthStateValidator`
    - `sign(studentId, nonce, now)` → HMAC-SHA256 encoded state with 10-minute TTL
    - `validate(state, now)` → true/false; return false (not throw) on expired or tampered state
    - Server HMAC secret loaded from a dedicated env var (never committed)
    - _Requirements: 4.4_

  - [ ] 12.4 Implement `EmailIntegrationServiceImpl` (MS365 path)
    - `authorizationUrl(studentId, MS365)` → build OAuth URL with signed state and
      `Mail.Read` scope
    - `handleCallback(code, state, MS365)` → validate state and take `studentId` from it,
      exchange code for tokens via Microsoft identity platform `/token` endpoint, encrypt and
      persist via `OAuthTokenStore`
    - `provider=GMAIL` → 422 `unsupported_provider` (Gmail deferred)
    - `fetchUnread(studentId, since)` → transparently refresh token if within 5-min expiry
      window (_Requirement 4.5_); call Graph `/me/mailFolders/Inbox/messages` with `$filter`
      for unread since timestamp, top 50; handle 429 with `Retry-After` back-off up to 3
      retries (1 s, 2 s, 4 s) (_Requirement 5.10_)
    - `connectedProvider()`, `revokeAndDisconnect()` — implement disconnect (_Requirement 4.7_)
    - Handle concurrent refresh race: use per-student lock to ensure single refresh
      (_Requirement 4.9_)
    - Return 503 with `email_reconnect_required` if provider rejects refresh token; delete
      stale row (_Requirement 4.6_)
    - _Requirements: 4.1, 4.2, 4.5, 4.6, 4.7, 4.8, 4.9_

  - [ ] 12.5 Implement `EmailController` (`GET /api/me/email/connect`, `GET /api/oauth/callback
         /{provider}`, `DELETE /api/me/email`, `GET /api/me/email/status`)
    - Wire `EmailIntegrationService` to HTTP layer per API definitions in design
    - Callback redirects to frontend with `?email=connected`
    - Status endpoint returns `{"connected": false}` or provider info
    - _Requirements: 4.1, 4.2, 4.7, 4.8_

  - [ ]* 12.6 Write unit tests for `EmailIntegrationService`
    - Test token refresh logic (within window, at boundary, provider rejection), concurrent
      refresh (only one refresh performed), encryption round-trip via `OAuthTokenStore`
    - _Requirements: 4.5, 4.6, 4.9_

- [ ] 13. *(Phase 1c — after ITS approval)* Implement `DigestService` and email digest endpoints
  - [ ] 13.1 Implement `DigestServiceImpl`
    - `runDigestForAll()`: query all students with a `connected_account` row; skip those whose
      last digest ran within 4 hours (_Requirement 5.7_)
    - `runDigest(studentId)`: fetch unread emails since last `email_digest.latest_email_at` (or
      midnight if no prior digest) via `EmailIntegrationService.fetchUnread()`; partition into
      batches of ≤ 20 (_Requirement 5.3_); for each batch build prompt with `<email>` tags and
      system instruction to treat as data (_Requirement 5.5_); call Claude to produce structured
      markdown digest (_Requirement 5.4_); concatenate batch summaries under "### Part n"
      headings into one digest (_Requirement 5.3_); store it as one `notification_log` row with
      type `EMAIL_DIGEST` and one `email_digest` row (_Requirement 5.6_)
    - Process each student in isolation — one student's failure must not affect others
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10_

  - [ ] 13.2 Wire `@Scheduled` cron for digest (`assistant.digest.cron`, default `0 0 7 * * *`)
    - Add `assistant.digest.cron` property to `AssistantProperties`
    - _Requirements: 5.1_

  - [ ] 13.3 Implement digest HTTP endpoints
    - `POST /api/me/email/digest` → call `DigestService.runDigest(studentId)` and return 202
    - `GET /api/me/email/digests?limit=N` → return N most recent digest summaries (join
      `notification_log` + `email_digest`) for the authenticated student
    - _Requirements: 5.8, 5.9_

  - [ ]* 13.4 Write property test for email batch partitioning (Property 2)
    - Use jqwik: for arbitrary non-empty `List<RawEmail>`, assert every batch ≤ 20, union =
      input, batches are pairwise disjoint
    - **Property 2: Email digest batch partitioning**
    - **Validates: Requirements 5.3**

  - [ ]* 13.5 Write unit tests for `DigestService`
    - Mock `EmailIntegrationService` and Claude; verify batch creation, skip logic (4-hour
      window), isolated failure handling, notification persistence
    - _Requirements: 5.1, 5.7, 5.10_

- [ ] 14. Implement notification polling endpoints
  - [ ] 14.1 Implement `NotificationController` (`GET /api/me/notifications`,
         `POST /api/me/notifications/{id}/read`)
    - `GET`: query `notification_log` for authenticated student ordered by `created_at DESC`,
      respect `?limit=N`
    - `POST /{id}/read`: set `read = true`; return 404 if notification belongs to another student
    - _Requirements: 6.1, 6.2_

  - [ ]* 14.2 Write unit tests for `NotificationController`
    - Verify correct student scoping (no cross-student access), read marking, limit parameter
    - _Requirements: 6.1, 6.2_

- [ ] 15. Phase 1 global wiring and error handling
  - [ ] 15.1 Implement global `@ControllerAdvice` error handler
    - Map `DataIntegrityViolationException` → 409 (by constraint name) or 500 `internal_error`
    - Map JWT exceptions → 401 with `missing_token`, `invalid_token`, `token_expired`
    - Map validation exceptions → 422 with `missing_required_field` or `invalid_*` codes
    - Never return stack traces, raw DB messages, or internal identifiers (_Requirement 13.5_)
    - _Requirements: 1.11, 13.5_

  - [ ] 15.2 Add the question retention job
    - `@Scheduled` daily: `DELETE FROM question WHERE asked_at < now() - interval '12 months'`
      (feedback rows go with it via `ON DELETE CASCADE`)
    - Make the retention period configurable (`assistant.retention.questions`, default `365d`)
    - (The `question.student_id` column itself is created in task 3 / V2.)
    - _Requirements: 13.8_

  - [ ]* 15.3 Write property test for email domain pattern validation (Property 8)
    - Use jqwik: for arbitrary email strings and the configured `emailDomainPattern`, assert
      matching emails get 201 and non-matching emails get 422 with `invalid_email_domain`
    - **Property 8: Email domain pattern validation**
    - **Validates: Requirements 1.2, 12.3**

- [ ] 16. Phase 1 checkpoint — full auth and digest flow
  - Ensure all Phase 1 tests pass, ask the user if questions arise.

---

### Phase 2 — eCampus Upload Bridge, Course Planner

- [ ] 17. Write Flyway migration V5 — course planner and completed courses
  - Create `V5__course_planner.sql`
  - Define `planner_semester` (id, student_id FK, label, ordinal) with
    `UNIQUE(student_id, ordinal)`
  - Define `planner_slot` (id, planner_semester_id FK, module_code, module_title, ects, status,
    notes) with index on `planner_semester_id`
  - Define `completed_course` (id, student_id FK, module_code, module_title, ects, grade
    NUMERIC(3,1), passed, semester) with `UNIQUE(student_id, module_code)`
  - _Requirements: 8.1, 8.7_

- [ ] 18. Implement `PlannerService` — semester and slot management
  - [ ] 18.1 Implement `PlannerServiceImpl` core CRUD
    - `getPlan(studentId)`: return all semesters ordered by ordinal with their slots
    - `createSemester(studentId, label, ordinal)`: insert; return 409 `semester_ordinal_exists`
      on duplicate ordinal (_Requirement 8.2_)
    - `addSlot(studentId, sid, draft)`: verify semester belongs to student (→ 404 if not);
      insert slot with initial status `planned` (_Requirement 8.3_)
    - `updateSlot(studentId, slotId, draft)`: verify ownership (→ 404); validate status
      transition against allowed transitions; persist (_Requirement 8.4, 8.6_)
    - `removeSlot(studentId, slotId)`: verify ownership (→ 404); delete (_Requirement 8.5_)
    - `recordCompleted(studentId, dto)`: insert into `completed_course`; 409
      `course_already_completed` on duplicate module code (_Requirement 8.7_)
    - Enforce `SlotStatus` machine: `planned→enrolled|completed|dropped`,
      `enrolled→completed|failed`, `failed→planned`; throw `IllegalStateException` for invalid
      transitions (surfaced as 422 `invalid_status_transition`) (_Requirement 8.6_)
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9_

  - [ ]* 18.2 Write property test for planner slot status machine (Property 5)
    - Use jqwik: for arbitrary `PlannerSlot` status and target `SlotStatus`, assert invalid
      transitions throw `IllegalStateException`, valid transitions persist new status
    - **Property 5: Planner slot status machine — valid and invalid transitions**
    - **Validates: Requirements 8.6**

  - [ ]* 18.3 Write unit tests for `PlannerServiceImpl`
    - Test ownership guards (wrong student → 404), duplicate semester ordinal, duplicate
      completed course, all valid and invalid status transitions
    - _Requirements: 8.2, 8.3, 8.5, 8.6, 8.7_

- [ ] 19. Implement `PlannerController` — planner REST endpoints
  - Wire all planner endpoints as defined in the API section of the design:
    `GET /api/me/planner`, `POST /api/me/planner/semesters`,
    `POST /api/me/planner/semesters/{sid}/slots`, `PUT /api/me/planner/slots/{id}`,
    `DELETE /api/me/planner/slots/{id}`, `POST /api/me/planner/completed`
  - Validate request bodies and return 400 for missing/out-of-range fields (_Requirements 8.8, 8.9_)
  - _Requirements: 8.1–8.9_

- [ ] 20. Implement AI-assisted course suggestions (`GET /api/me/planner/suggestions`)
  - [ ] 20.1 Implement `PlannerService.suggestNext(studentId)`
    - Build query from student's `degree_program` and list of `completed_course` module codes
    - Call `ChunkSearch.search(query, topK)` to retrieve module catalog chunks from the RAG index
    - Pass retrieved chunks + completed modules to Claude to generate ranked
      `CourseSuggestionDto` list
    - Include `"liveDataAvailable": false` in response (Phase 2 upload-bridge mode)
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [ ] 20.3 Extend `StudentContextBuilder` with Phase 2 sections
    - Add academic snapshot (completed modules, ECTS earned) and active planner slots to the
      `<student-context>` block; apply the full drop order from Requirement 3.4
    - Extend the Property 1 test inputs with completed courses and plan slots
    - _Requirements: 3.2, 3.4_

  - [ ]* 20.2 Write unit tests for `suggestNext()`
    - Mock `ChunkSearch` and Claude; verify query construction uses degree program and completed
      modules; verify `liveDataAvailable: false` is set
    - _Requirements: 9.4_

- [ ] 21. Implement `ECampusDataParser` — PDF transcript and CSV enrollment parsing
  - [ ] 21.0 Collect sample exports first
    - Get 2–3 real eCampus transcript PDFs and enrollment/exam CSV exports (own data, or
      anonymized from fellow students) and save anonymized copies as test fixtures; the parser
      rules in 21.1/21.2 are written against these, not guessed
    - _Requirements: 7.4, 7.5_

  - [ ] 21.1 Implement `ECampusDataParserImpl.parseTranscript(InputStream)`
    - Use PDFBox (`PDDocument`, `PDFTextStripper`) to extract text from uploaded PDF
    - Parse module codes, titles, ECTS (int), grades (NUMERIC 1.0–5.0 in 0.1 steps or NULL
      for pass/fail), and semester labels from extracted text
    - Return `ParseResult` with `completedCourses` list, empty `enrolledCourses`, and
      `warnings` for unparseable lines
    - Return `ParseResult` with empty `completedCourses` (triggers 422 `unparseable_file` in
      controller) if zero records are found (_Requirement 7.7_)
    - Discard PDF bytes immediately after extraction (_Requirement 7.9_)
    - _Requirements: 7.1, 7.4, 7.7, 7.9_

  - [ ] 21.2 Implement `ECampusDataParserImpl.parseEnrollmentExport(InputStream, String)`
    - Detect TSV vs. semicolon-delimited CSV; extract enrolled course names and exam dates
    - Return enrolled courses with status `enrolled` and exam dates as `ExamDateRecord` list
    - Collect unparseable rows in `warnings` but still save successfully parsed rows
      (_Requirement 7.8_)
    - _Requirements: 7.1, 7.5, 7.8_

  - [ ]* 21.3 Write unit tests for `ECampusDataParser`
    - Test PDF extraction with a sample text block, TSV and CSV parsing, grade parsing edge
      cases (pass/fail NULL, boundary values 1.0 and 5.0), warnings for ambiguous rows
    - _Requirements: 7.4, 7.5, 7.8_

- [ ] 22. Implement eCampus upload controller and persistence
  - [ ] 22.1 Implement `EcampusController` (`POST /api/me/ecampus/upload`,
         `GET /api/me/ecampus/imports`)
    - `POST /api/me/ecampus/upload`: accept multipart file up to 5 MB (enforce at controller
      layer with `@RequestParam` + size check → 413 `file_too_large`); detect MIME type; call
      `parseTranscript` or `parseEnrollmentExport`; persist results via `PlannerService`
      (completed courses → `completed_course`, enrolled courses → `planner_slot` status
      `enrolled`); create `DEADLINE` notifications in `notification_log` for future exam dates
      (_Requirement 7.6_); return 200 with import counts and warnings; never store raw bytes
      (_Requirement 7.9, 7.10_)
    - `GET /api/me/ecampus/imports?limit=N`: return N most recent upload summaries (1 ≤ N ≤ 50);
      return 422 `invalid_limit` if out of range (_Requirement 7.11_)
    - _Requirements: 7.1, 7.2, 7.3, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11_

  - [ ]* 22.2 Write unit tests for `EcampusController`
    - Test 5 MB limit enforcement, unsupported MIME type rejection, partial-parse warnings
      returned, DEADLINE notifications created for future exam dates only
    - _Requirements: 7.2, 7.3, 7.6, 7.8_

- [ ] 23. Phase 2 checkpoint — planner and eCampus bridge
  - Ensure all Phase 1 and Phase 2 tests pass, ask the user if questions arise.

---

### Phase 3 — Wellness Reminders, Campus Events

- [ ] 24. Implement wellness rule engine and `WellnessReminderService`
  - [ ] 24.1 Implement `WellnessRule` interface and four built-in rules
    - `OptInRule`: block unless `wellness_enabled` preference is `true` (_Requirement 10.2_)
    - `QuietHoursRule`: block if now (university time zone) is inside
      [`quietHoursStart`, `quietHoursEnd`); handle windows that cross midnight (_Requirement 10.2_)
    - `CooldownRule`: query `notification_log` for last notification of same type; block if
      within the effective cooldown (_Requirement 10.2_)
    - `ExamPeriodRule`: never blocks; if a `DEADLINE` notification falls within the next 72
      hours, set the effective cooldown to twice the standard one (_Requirement 10.2_)
    - `WellnessContext` record: carries current time, studentId, nudge type, and cooldown config
    - _Requirements: 10.1, 10.2_

  - [ ]* 24.2 Write property test for wellness rule chain AND logic (Property 6)
    - Use jqwik: for arbitrary lists of `WellnessRule` stubs, assert `shouldSend() = false`
      when any rule returns false, and `shouldSend() = true` only when all rules return true
    - **Property 6: Wellness rule chain AND logic**
    - **Validates: Requirements 10.2**

  - [ ]* 24.3 Write property test for notification cooldown invariant (Property 3)
    - Use jqwik: for arbitrary `studentId`, `NotificationType`, `cooldown`, and `Instant`,
      assert `CooldownRule.allows()` returns false when last notification is within cooldown
      window and true otherwise
    - **Property 3: Notification cooldown invariant**
    - **Validates: Requirements 10.2**

  - [ ] 24.4 Implement `WellnessReminderServiceImpl.evaluateAll()`
    - Query all opted-in students; for each, evaluate the rule chain via `shouldSend()`
    - When all rules pass: call Claude with minimal prompt (nudge type, time of day, first name
      only — no other personal data) to generate ≤ 40 word message (_Requirement 10.3_)
    - Store each successfully generated nudge in `notification_log` with type `WELLNESS`
      (_Requirement 10.4_)
    - On Claude failure: store nothing, log WARN; no retry until next scheduled cycle
      (_Requirement 10.5_)
    - Wire `@Scheduled(fixedDelay = 1_800_000)` (_Requirement 10.1_)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 24.5 Write unit tests for `WellnessReminderServiceImpl`
    - Mock rule chain, `ProfileService`, and Claude; verify all-pass → Claude called,
      any-fail → Claude not called, Claude failure → WARN logged and nudge skipped
    - _Requirements: 10.2, 10.5_

- [ ] 25. Implement `WellnessController` — settings and notifications endpoints
  - [ ] 25.1 Implement `PUT /api/me/wellness/settings`
    - Validate `enabled` (boolean), `quietHoursStart`, `quietHoursEnd` (HH:mm 00:00–23:59,
      end must differ from start; end < start means the window crosses midnight); persist as
      preferences via `ProfileService.setPreference()`
    - Return 204 on success; 422 `invalid_wellness_settings` on format/range error or end = start
    - _Requirements: 10.6, 10.7_

  - [ ] 25.2 Implement `GET /api/me/wellness/notifications?limit=N`
    - Return N most recent `WELLNESS` type notifications for the student (1 ≤ N ≤ 100); return
      422 `invalid_limit` if N is outside range
    - _Requirements: 10.8, 10.9_

  - [ ]* 25.3 Write unit tests for `WellnessController`
    - Test time-format validation, end = start rejection, a 22:00–08:00 window accepted, limit
      range enforcement
    - _Requirements: 10.7, 10.9_

- [ ] 26. Implement campus event suggestion via RAG
  - [ ] 26.1 Implement `WellnessReminderService` campus event daily job
    - Schedule once per day: call `ChunkSearch.search(terms, 5)` where terms come from
      `assistant.university.event-search-terms` (_Requirement 11.1_)
    - Pass top-5 results to Claude with a prompt requesting a brief event summary tailored to
      the student's degree program (_Requirement 11.2_)
    - Store result in `notification_log` with type `EVENT` (_Requirement 11.3_)
    - _Requirements: 11.1, 11.2, 11.3_

  - [ ]* 26.2 Write unit tests for campus event suggestion
    - Mock `ChunkSearch` and Claude; verify event-search-terms are used, type is `EVENT`
    - _Requirements: 11.1, 11.3_

- [ ] 27. Final checkpoint — all phases complete
  - Ensure all tests across Phase 1, 2, and 3 pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery
- Property tests require `net.jqwik:jqwik:1.9.2` (added in Task 1) and annotate test classes
  with `@ExtendWith(JqwikExtension.class)` or use `@Property` directly on methods
- All new `JdbcClient` usage follows the existing pattern in `AnswerService` and `ChunkSearch`
- `JWT_PRIVATE_KEY_PATH` must be set before running Phase 1 integration tests;
  `ACCOUNT_ENCRYPTION_KEY` only from Phase 1c (email)
- Testcontainers-based tests need a local Docker runtime (e.g. Docker Desktop or Colima)
- Flyway migrations must run in order; never modify a committed migration file
- The behaviour of the anonymous `POST /api/ask` endpoint never changes (Requirement 3.7); task
  0.1 only adds an overload behind it
- Phase 3 does not include an `ECampusSessionClient` implementation; it is not planned unless the
  university provides an official API or written approval (`docs/decisions.md` #3)

### Phase 4 — Agents

- [x] 28.1 `Agent` contract, `AgentRequest`, `AgentReply` (cards, typed actions), `Concierge` with rule-based routing and help
- [x] 28.2 `RegulationsAgent` (wraps `AnswerService`, shared `StudentContext`)
- [x] 28.3 `DeadlineAgent` with `WhenParser` (EN/DE days, dates, times), reminders and "what's coming up"
- [x] 28.4 `OnboardingAgent` (journey progress and next steps)
- [x] 28.5 `CampusAgent` (events by time window, news)
- [x] 28.6 `POST /api/assistant`; chat shows the agent, cards and one-tap actions; reminders in to-do, bell and history
- [x] 28.7 Tests: `WhenParserTests`, `ConciergeTests`, `DeadlineAgentTests`
- [ ] 28.8 *(login)* Store reminders, journey progress and agent settings per student (migration + service)
- [ ] 28.9 *(login)* Background triggers: scheduled Deadline and Campus runs that write `notification_log`; run log table
- [ ] 28.10 *(login)* Agent settings page: switch agents on/off, show the data each one uses
- [ ] 28.11 *(Claude)* Replace rule-based routing with tool use over the same agents; keep rule-based routing as the offline fallback
- [ ] 28.12 *(login + ITS)* Inbox agent on top of tasks 12–13
- [ ] 28.13 *(login + eCampus export)* Study planner agent on top of tasks 18–22

### Phase 2 (pre-login part) — Study planner, built 2026-10-16

- [x] 29.1 Import programmes from the module database: regulations (areas, focus options, credits, thesis rules) and module descriptions (offering term, exam form, prerequisites); weekly + on startup + admin endpoint; snapshot table `programme_snapshot` (V3)
- [x] 29.2 `StudyPlanner`: select modules per requirement (completed and preferred first, exact credit fit, oral-exam rule), schedule by offering term, thesis last after 60 CP, rebalance overloaded semesters; `evaluate` checks edited plans
- [x] 29.3 `GET /api/programmes`, `POST /api/planner/plan`, `POST /api/planner/check`
- [x] 29.4 Planner page (semester columns, drag and drop, move/remove/mark passed, module picker, requirement progress, warnings in EN/DE) and start-page card; plan stored in the browser
- [x] 29.5 Planner agent in the chat ("plan my master in 5 semesters with the focus CIDA")
- [x] 29.6 Optional past module data (`PLANNER_HISTORY_FILE`, see docs/past-data-request.md)
- [ ] 29.7 *(login)* Store plans per student on the server; fill completed modules from the eCampus transcript upload (tasks 21–22)
- [ ] 29.8 More programmes: add their regulations URLs to `assistant.planner.programmes` and check the import
- [ ] 29.9 Weekly timetable view (lecture slots, free-day filter) once the university provides timetable data officially

### Known issues (2026-10-11)

- Keyword retrieval often misses the right page (generic words outrank rare ones), so many
  answers say "not in the sources" although the page is indexed.
- The local 7B model sometimes answers in German to English questions and invents links, email
  addresses or systems that are not in the sources. Claude should do better, but retrieval has to
  be fixed either way.
- The crawl stopped at `max-pages: 800` before reaching the module database (Prüfungsordnung,
  Modulhandbuch).

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["0.1", "0.2"] },
    { "id": 1, "tasks": ["0.3", "1"] },
    { "id": 2, "tasks": ["2.1", "3", "5"] },
    { "id": 3, "tasks": ["2.2", "6.1"] },
    { "id": 4, "tasks": ["6.2", "7.1"] },
    { "id": 5, "tasks": ["6.3", "7.2", "7.3", "7.6"] },
    { "id": 6, "tasks": ["7.4", "7.5", "8.1"] },
    { "id": 7, "tasks": ["8.2", "8.3", "8.4"] },
    { "id": 8, "tasks": ["10.1"] },
    { "id": 9, "tasks": ["10.2", "11.1"] },
    { "id": 10, "tasks": ["11.2", "11.3"] },
    { "id": 11, "tasks": ["14.1", "14.2"] },
    { "id": 12, "tasks": ["15.1", "15.2"] },
    { "id": 13, "tasks": ["15.3"] },
    { "id": 14, "tasks": ["4", "12.1"], "note": "Phase 1c — only after ITS approval" },
    { "id": 15, "tasks": ["12.2", "12.3"] },
    { "id": 16, "tasks": ["12.4"] },
    { "id": 17, "tasks": ["12.5", "12.6", "13.1"] },
    { "id": 18, "tasks": ["13.2", "13.3", "13.4", "13.5"] },
    { "id": 19, "tasks": ["17", "21.0"] },
    { "id": 20, "tasks": ["18.1"] },
    { "id": 21, "tasks": ["18.2", "18.3", "19"] },
    { "id": 22, "tasks": ["20.1", "20.3", "21.1", "21.2"] },
    { "id": 23, "tasks": ["20.2", "21.3", "22.1"] },
    { "id": 24, "tasks": ["22.2"] },
    { "id": 25, "tasks": ["24.1"] },
    { "id": 26, "tasks": ["24.2", "24.3", "24.4"] },
    { "id": 27, "tasks": ["24.5", "25.1"] },
    { "id": 28, "tasks": ["25.2", "25.3", "26.1"] },
    { "id": 29, "tasks": ["26.2"] }
  ]
}
```
