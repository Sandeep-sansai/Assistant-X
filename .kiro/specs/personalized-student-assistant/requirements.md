# Requirements Document

## Introduction

The Personalized University Student Assistant extends the existing stateless RAG-based Q&A system
into an identity-aware, multi-service academic companion. It adds user authentication, a persistent
student profile, personalized AI answers, email digest integration, a semester course planner,
eCampus data import (via file upload — no credentials, no scraping), wellness reminders, and campus
event suggestions. All university-specific values (institution name, email domain pattern, portal
URL, grade scale, SSO type) are driven by YAML configuration so that a second university can be
onboarded without code changes.

The system is delivered in three incremental phases:
- **Phase 1** — Authentication, profile, personalized Q&A, email digest
- **Phase 2** — Course planner, eCampus upload bridge
- **Phase 3** — Wellness reminders, campus events, consent-gated eCampus session client

---

## Glossary

- **System**: The Spring Boot backend application.
- **AuthFilter**: The JWT validation filter that guards all `/api/me/*` endpoints.
- **ProfileService**: The service that owns and persists the student identity and preference model.
- **StudentContextBuilder**: The service that assembles student data into a token-budgeted XML context block.
- **PersonalizedAnswerService**: The service that wraps `AnswerService` and injects student context into Claude prompts.
- **EmailIntegrationService**: The service that manages OAuth 2.0 token lifecycle and fetches emails from MS365 or Gmail.
- **DigestService**: The service that periodically fetches emails, summarises them with Claude, and stores the result.
- **PlannerService**: The service that manages semester-by-semester course plans and AI-assisted suggestions.
- **WellnessReminderService**: The service that evaluates rule conditions and dispatches proactive health nudges.
- **ECampusDataParser**: The service that parses student-uploaded eCampus export files (PDF transcripts, CSV enrollments).
- **OAuthStateFactory / OAuthStateValidator**: Components that sign and verify the OAuth CSRF state parameter.
- **TokenStore**: The encrypted storage layer for OAuth access and refresh tokens (`connected_account` table).
- **NotificationLog**: The `notification_log` database table and its associated read/write service.
- **JWT**: JSON Web Token, RS256-signed, used to identify authenticated students in every API request.
- **Student**: An authenticated university user with a profile record in the `student` table.
- **PlannerSlot**: A single course entry within a `PlannerSemester`, carrying a status from the planner state machine.
- **SlotStatus**: One of `planned`, `enrolled`, `completed`, `failed`, `dropped`.
- **WellnessRule**: A single guard condition evaluated by `WellnessReminderService` before dispatching a nudge.
- **eCampus**: The student-facing brand of HISinOne campus management system used at Uni Kassel.
- **UniversityProperties**: A Spring configuration record holding all university-specific YAML values.

---

## Requirements

### Requirement 0: Start Page (built)

**Phase: 0 — built before accounts, see `docs/decisions.md` #12 and #13**

**User Story:** As a student, I want a start page that shows what is coming up and what is
happening in my department, and suggests what to ask, so that I don't miss deadlines or events.

#### Acceptance Criteria

1. WHEN a student opens the start page for the first time, THE System SHALL ask for department,
   programme and semester, store them only in the browser, and allow skipping.
2. THE System SHALL show upcoming key dates from `academic-calendar.yml`, nearest first, with a
   countdown, the source link and the date the entry was last checked.
3. THE System SHALL show upcoming events (next 120 days) and recent news (last 60 days) for the
   chosen department plus university-wide items, read from `assistant.feed.sources` at least every
   6 hours; IF the department has no configured sources, THEN it SHALL say so and show
   university-wide items.
4. THE System SHALL offer suggested questions, led by the suggestions of the nearest key dates.
5. THE System SHALL show to-dos for key dates that carry an action and start within 30 days (or
   started within the last 14), which the student can tick off, and a notification list of due
   to-dos, events today or tomorrow, and news since the last visit.
6. WHEN a question is asked with a profile, THE System SHALL include it in the prompt as a
   `<student-context>` block and SHALL NOT store it.

When accounts exist (Requirements 1–3), the profile, ticked to-dos and read notifications move
from the browser to the server.

---

### Requirement 1: User Registration and Login

**Phase: 1**

**User Story:** As a university student, I want to register and log in with my university email
address and password, so that I can access personalized features securely.

#### Acceptance Criteria

1. WHEN a student submits a valid university email address, a password between 8 and 128
   characters, and a display name between 1 and 100 characters to `POST /api/auth/register`, THE
   System SHALL create a student account, return a JWT token and the new `studentId` with HTTP 201,
   and store the password as a bcrypt hash (cost 12).
2. IF the email address submitted to `POST /api/auth/register` does not match the configured
   `assistant.university.email-domain-pattern`, THEN THE System SHALL reject the request with
   HTTP 422 and error code `invalid_email_domain`.
3. IF the email address submitted to `POST /api/auth/register` is already registered, THEN THE
   System SHALL return HTTP 409 with error code `email_already_registered`.
4. IF any required field submitted to `POST /api/auth/register` is missing or empty, THEN THE
   System SHALL return HTTP 422 with error code `missing_required_field`, and no account SHALL be
   created.
5. WHEN a student submits a registered email address and correct password to `POST /api/auth/login`,
   THE System SHALL return a JWT token and the student's `studentId` with HTTP 200.
6. IF the email address submitted to `POST /api/auth/login` is not registered, THEN THE System
   SHALL return HTTP 401 with error code `invalid_credentials`.
7. IF the password submitted to `POST /api/auth/login` does not match the stored hash, THEN THE
   System SHALL return HTTP 401 with error code `invalid_credentials`.
8. THE System SHALL sign all JWT tokens with RS256 using the private key loaded from
   `JWT_PRIVATE_KEY_PATH`; tokens SHALL expire after 24 hours.
9. WHEN a student calls `POST /api/auth/refresh` with a valid JWT that expired within the last
   7 days, THE System SHALL issue a new JWT with a fresh 24-hour expiry.
10. IF a JWT presented to `POST /api/auth/refresh` is expired beyond the 7-day refresh window,
    THEN THE System SHALL return HTTP 401 with error code `token_expired`, and no new token SHALL
    be issued.
11. IF a JWT presented to any protected endpoint is missing, malformed, or unsigned, THEN THE
    AuthFilter SHALL return HTTP 401 with error code `missing_token` or `invalid_token`
    respectively, without leaking stack traces or internal identifiers.
12. WHEN a student account is created, THE System SHALL send a single-use verification link
    (valid for 24 hours) to the registered email address; the account SHALL remain unverified
    until the link is opened. In the `dev` profile the link SHALL be written to the application
    log instead of being emailed.
13. IF an unverified student submits correct credentials to `POST /api/auth/login`, THEN THE
    System SHALL return HTTP 403 with error code `email_not_verified` and SHALL NOT issue a JWT.
14. THE System SHALL keep the following paths reachable without a JWT: `/`, static resources,
    `POST /api/ask`, `POST /api/questions/{id}/feedback`, `/api/auth/**`,
    `GET /api/oauth/callback/{provider}`, `/api/admin/**` (protected by its own admin token), and
    `/actuator/health`. All `/api/me/**` paths SHALL require a valid JWT.

---

### Requirement 2: Student Profile Management

**Phase: 1**

**User Story:** As a student, I want to view and update my academic profile (degree program,
enrollment year, target graduation semester), so that the assistant can tailor answers to my
situation.

#### Acceptance Criteria

1. WHEN an authenticated student calls `GET /api/me`, THE System SHALL return a
   `StudentProfileDto` containing the student's email, display name, degree program, enrollment
   year, and target semester.
2. WHEN an authenticated student submits updated profile fields to `PUT /api/me`, THE ProfileService
   SHALL persist changes to `display_name` (max 100 characters), `degree_program` (max 200
   characters), `enrollment_year` (1900–2100 inclusive), and `target_semester` (1–20 inclusive),
   and return HTTP 204; read-only fields (email, `studentId`) SHALL be ignored if present in the
   request body; IF the request body contains no updatable fields or all submitted field values
   fail validation, THEN THE System SHALL return HTTP 422 with error code `invalid_profile_data`.
3. WHEN an authenticated student calls `GET /api/me/preferences`, THE System SHALL return the
   complete key-value map of that student's preferences.
4. WHEN an authenticated student calls `PUT /api/me/preferences/{name}` with a value, THE
   ProfileService SHALL upsert the preference and return HTTP 204; an existing value for the same
   name SHALL be replaced without error; IF `{name}` exceeds 100 characters or `value` exceeds
   500 characters, THEN THE System SHALL return HTTP 422 with error code `preference_value_too_long`.
5. WHEN a new student account is successfully created via `POST /api/auth/register`, THE System
   SHALL create a `student` row with `display_name` set to the submitted display name and
   `degree_program` set to an empty string.
6. WHEN an authenticated student makes any request to a protected endpoint, THE System SHALL
   update `last_seen_at` for that student to the current timestamp.

---

### Requirement 3: Personalized Q&A

**Phase: 1**

**User Story:** As a student, I want to ask the assistant questions that are answered with
awareness of my degree program, completed courses, and academic plan, so that the responses are
directly relevant to my situation.

#### Acceptance Criteria

1. WHEN an authenticated student submits a question (≤ 1 000 characters) to `POST /api/me/ask`,
   THE PersonalizedAnswerService SHALL return an answer using the same DTO shape as `/api/ask`.
2. THE StudentContextBuilder SHALL assemble a `<student-context>` XML block containing the
   student's profile facts, preferences, and any DEADLINE notifications from the last 14 days
   before each call to `PersonalizedAnswerService`. From Phase 2 on, the block SHALL also contain
   the academic snapshot (completed modules, ECTS earned) and active planner slots.
3. THE PersonalizedAnswerService SHALL prepend the `<student-context>` block to the system prompt
   sent to Claude, including an instruction to treat the block as context and tailor the answer
   accordingly.
4. THE StudentContextBuilder SHALL ensure the rendered context block does not exceed
   `assistant.personalization.context-max-tokens` × 4 characters (default: 2 000 tokens × 4
   chars); WHEN the budget would be exceeded, THE StudentContextBuilder SHALL drop sections in
   this priority order: first dropping completed modules older than 2 years, then preferences,
   then plan slots; DEADLINE notifications SHALL always be retained regardless of budget pressure.
5. THE System SHALL cache the rendered context string per student for up to 5 minutes using an
   in-memory cache (Caffeine) to avoid repeated database reads on every question.
6. WHEN a personalized question is answered, THE System SHALL persist the question exactly once
   in the `question` table with the student's `student_id` FK populated; anonymous questions from
   `POST /api/ask` SHALL continue to be stored with `student_id` NULL.
7. THE existing `POST /api/ask` anonymous endpoint SHALL remain unchanged and fully functional.
8. IF a question submitted to `POST /api/me/ask` exceeds 1 000 characters, THEN THE System SHALL
   return HTTP 422 with error code `question_too_long` without calling Claude.
9. IF the JWT presented to `POST /api/me/ask` is missing or invalid, THEN THE AuthFilter SHALL
   return HTTP 401 before any Claude call is made.
10. IF `StudentContextBuilder` fails to assemble the context block (e.g. due to a transient DB
    error), THEN THE PersonalizedAnswerService SHALL proceed with an empty context block and
    answer the question using only the RAG pipeline, logging the failure at WARN level.

---

### Requirement 4: Email Account Connection (OAuth 2.0)

**Phase: 1**

**User Story:** As a student, I want to connect my university Microsoft 365 account to the
assistant, so that it can summarize my inbox for me.

**Precondition:** Requirements 4 and 5 are built only after the university IT service (ITS) has
confirmed that the app registration may request `Mail.Read` in the university Microsoft 365
tenant (admin consent is usually required) and the data-protection questions of sending email
content to Claude are settled. Gmail support is deferred; `provider=GMAIL` SHALL return HTTP 422
with error code `unsupported_provider` until it is implemented.

#### Acceptance Criteria

1. WHEN an authenticated student calls `GET /api/me/email/connect?provider=MS365`, THE
   EmailIntegrationService SHALL return an authorization URL pointing to the provider's consent
   screen.
2. WHEN the OAuth provider redirects back to `GET /api/oauth/callback/{provider}` with a valid
   authorization code and state, THE EmailIntegrationService SHALL exchange the code for tokens and
   store them encrypted in `connected_account`; THE System SHALL then redirect the student to the
   frontend with `?email=connected`. Because this request is a browser redirect from the provider
   and carries no `Authorization` header, the callback SHALL NOT require a JWT; THE System SHALL
   take the `studentId` exclusively from the validated state parameter.
3. THE EmailIntegrationService SHALL encrypt all OAuth access tokens and refresh tokens using
   AES-256-GCM before writing them to `connected_account`; the encryption key SHALL be loaded
   exclusively from the `ACCOUNT_ENCRYPTION_KEY` environment variable and SHALL never be committed
   to source control or logged.
4. THE System SHALL embed a short-lived (10-minute TTL) HMAC-SHA256-signed state parameter
   encoding `studentId` and a random nonce in the OAuth authorization URL, and SHALL validate the
   state on callback to prevent CSRF; IF the state is invalid or expired, THEN THE System SHALL
   return HTTP 400.
5. WHEN `expires_at - 5 minutes ≤ now()`, THE EmailIntegrationService SHALL transparently refresh
   the access token using the stored refresh token before making any provider API call.
6. IF the provider rejects the refresh token (revoked or requiring re-consent), THEN THE System
   SHALL return HTTP 503 with error code `email_reconnect_required` and delete the stale
   `connected_account` row.
7. WHEN an authenticated student calls `DELETE /api/me/email`, THE System SHALL revoke the token
   at the provider, delete the `connected_account` row, and return HTTP 204.
8. WHEN an authenticated student calls `GET /api/me/email/status`, THE System SHALL return the
   connected provider name and provider email, or `"connected": false` if no account is linked.
9. WHEN two concurrent requests both detect token expiry for the same student, THE
   EmailIntegrationService SHALL ensure only one refresh is performed; the second SHALL reuse the
   newly obtained token without error.

---

### Requirement 5: Email Digest Generation

**Phase: 1**

**User Story:** As a student with a connected email account, I want the assistant to summarize my
unread university emails every morning, so that I can quickly catch up on deadlines and important
notices.

#### Acceptance Criteria

1. THE DigestService SHALL run `runDigestForAll()` daily on the cron schedule configured in
   `assistant.digest.cron` (default: 07:00 every day) for all students with connected email
   accounts.
2. WHEN generating a digest, THE EmailIntegrationService SHALL fetch unread emails since the
   `latest_email_at` of the student's last digest (or since midnight if no prior digest exists),
   up to 50 messages, using the appropriate provider API (`/me/mailFolders/Inbox/messages` for
   MS365 or `users.messages.list` for Gmail).
3. THE DigestService SHALL partition fetched emails into batches of at most 20 emails per Claude
   call; no email SHALL be omitted from, or duplicated across, the batches. WHEN there is more
   than one batch, THE DigestService SHALL concatenate the batch summaries under one heading per
   batch into a single digest, so that one run produces exactly one `EMAIL_DIGEST` notification.
4. WHEN summarising a batch, THE DigestService SHALL instruct Claude to identify urgent deadlines,
   registration windows, important admin notices, and other items, and to format the output as a
   structured markdown digest.
5. THE DigestService SHALL wrap raw email content in `<email>` tags in the Claude prompt and
   include a system-prompt instruction to treat that content as data only, consistent with the
   existing `<source>` pattern.
6. WHEN a digest is produced, THE DigestService SHALL store the summary in `notification_log`
   with type `EMAIL_DIGEST` and also insert a row in `email_digest` recording the email count,
   earliest and latest email timestamps, and raw subject list.
7. THE DigestService SHALL skip a student whose last digest was run within the last 4 hours.
8. WHEN an authenticated student calls `POST /api/me/email/digest`, THE DigestService SHALL run an
   on-demand digest for that student and return HTTP 202.
9. WHEN an authenticated student calls `GET /api/me/email/digests?limit=N`, THE System SHALL
   return the N most recent digest summaries for that student.
10. WHEN the email provider returns HTTP 429, THE EmailIntegrationService SHALL read the
    `Retry-After` header and retry up to 3 times with exponential back-off (1 s, 2 s, 4 s); IF all
    retries are exhausted, THEN THE DigestService SHALL skip that student's digest and schedule a
    retry for the next run without failing other students.

---

### Requirement 6: Notification Polling

**Phase: 1**

**User Story:** As a student, I want to see all assistant-generated notifications (digests,
deadlines, wellness nudges) in one place, so that I never miss anything important.

#### Acceptance Criteria

1. WHEN an authenticated student calls `GET /api/me/notifications?limit=N`, THE System SHALL
   return the N most recent notifications from `notification_log` for that student, ordered by
   `created_at` descending, with all notification types included.
2. WHEN an authenticated student calls `POST /api/me/notifications/{id}/read`, THE System SHALL
   mark the notification as read (`read = true`) and return HTTP 204; IF the notification does not
   belong to the requesting student, THEN THE System SHALL return HTTP 404.
3. THE System SHALL support client-side polling every 60 seconds in Phase 1 and Phase 2 (Web Push
   via VAPID is deferred to Phase 3).

---

### Requirement 7: eCampus Data Upload Bridge

**Phase: 2**

**User Story:** As a student, I want to upload my grade transcript PDF or enrollment CSV export
from eCampus to the assistant, so that the assistant can automatically populate my course history
and planner without me having to enter everything manually.

#### Acceptance Criteria

1. WHEN an authenticated student submits a multipart file upload to `POST /api/me/ecampus/upload`,
   THE ECampusDataParser SHALL accept PDF files (grade transcripts) and plain-text or
   semicolon/tab-separated CSV files (enrollment and exam schedule exports).
2. IF the uploaded file exceeds 5 MB, THEN THE System SHALL return HTTP 413 with error code
   `file_too_large`.
3. IF the uploaded file is not a recognized format (neither PDF nor CSV/TSV), THEN THE System
   SHALL return HTTP 422 with error code `unsupported_file_type`.
4. WHEN a PDF transcript is parsed, THE ECampusDataParser SHALL extract module codes, titles,
   ECTS credits, grades (as a decimal value between 1.0 and 5.0 in 0.1 increments, or NULL for
   pass/fail), and semester labels and store the records in the `completed_course` table via
   `PlannerService`; IF a module code already exists for that student in `completed_course`, THE
   parser SHALL skip that record and include it in the `warnings` array without returning an error.
5. WHEN a CSV enrollment export is parsed, THE ECampusDataParser SHALL extract enrolled course
   names and store them in `planner_slot` with status `enrolled` via `PlannerService`.
6. WHEN exam dates are extracted from any upload, THE ECampusDataParser SHALL create a `DEADLINE`
   notification in `notification_log` for each exam date that is strictly in the future relative
   to the upload timestamp.
7. IF a PDF upload yields zero parseable course records, THEN THE System SHALL return HTTP 422
   with error code `unparseable_file` and a detail message suggesting the plain-text paste
   fallback.
8. IF some rows in an upload cannot be parsed (ambiguous format), THEN THE System SHALL return
   HTTP 200 with a non-empty `warnings` array listing the unparsed rows; successfully parsed
   records SHALL still be saved.
9. THE ECampusDataParser SHALL discard raw file bytes immediately after parsing; file content
   SHALL never be stored in the database or forwarded to external services.
10. THE System SHALL make no outbound network connection to eCampus during the upload process; no
    student portal credentials SHALL be requested or stored.
11. WHEN an authenticated student calls `GET /api/me/ecampus/imports?limit=N` where N is between
    1 and 50, THE System SHALL return the N most recent upload summaries, each including timestamp,
    imported record counts, and any warnings; IF N is outside this range, THE System SHALL return
    HTTP 422 with error code `invalid_limit`.

---

### Requirement 8: Course Planner — Semester and Slot Management

**Phase: 2**

**User Story:** As a student, I want to organize my courses into a semester-by-semester plan,
track each course's status, and record completed courses with grades, so that I have a clear
picture of my academic progress.

#### Acceptance Criteria

1. WHEN an authenticated student calls `GET /api/me/planner`, THE PlannerService SHALL return all
   planner semesters for that student, each containing its slots, ordered by ordinal ascending.
2. WHEN an authenticated student calls `POST /api/me/planner/semesters` with a label of 1–100
   characters and an ordinal between 1 and 20, THE PlannerService SHALL create a `planner_semester`
   row and return HTTP 201; IF a semester with the same ordinal already exists for that student,
   THE PlannerService SHALL return HTTP 409 with error code `semester_ordinal_exists`.
3. WHEN an authenticated student adds a slot via `POST /api/me/planner/semesters/{sid}/slots`
   with a module code and title, THE PlannerService SHALL create a `planner_slot` row with initial
   status `planned` and return HTTP 201; IF the referenced semester `{sid}` does not belong to the
   authenticated student, THEN THE PlannerService SHALL return HTTP 404.
4. WHEN an authenticated student updates a slot via `PUT /api/me/planner/slots/{id}` with at
   least one valid field, THE PlannerService SHALL persist the changes and return HTTP 204; IF the
   slot `{id}` does not belong to the authenticated student, THEN THE PlannerService SHALL return
   HTTP 404.
5. WHEN an authenticated student calls `DELETE /api/me/planner/slots/{id}`, THE PlannerService
   SHALL remove the slot and return HTTP 204; IF the slot `{id}` does not belong to the
   authenticated student, THEN THE PlannerService SHALL return HTTP 404.
6. THE PlannerService SHALL enforce the following status transitions for `PlannerSlot`; IF an
   attempt is made to apply a transition not listed below, THEN THE PlannerService SHALL reject it
   with HTTP 422 and error code `invalid_status_transition`:
   - `planned` → `enrolled`, `completed`, `dropped`
   - `enrolled` → `completed`, `failed`
   - `failed` → `planned`
7. WHEN an authenticated student calls `POST /api/me/planner/completed` with a module code of
   1–30 characters, a title of 1–200 characters, an ECTS value between 1 and 30, a grade between
   1.0 and 5.0 (inclusive, in 0.1 increments), and a semester label of 1–100 characters, THE
   PlannerService SHALL insert a row into `completed_course` and return HTTP 201; IF the module
   code is already recorded for that student, THEN THE PlannerService SHALL return HTTP 409 with
   error code `course_already_completed`.
8. IF a `POST /api/me/planner/semesters` request is missing required fields or contains
   out-of-range values, THEN THE PlannerService SHALL return HTTP 400 indicating which fields are
   invalid.
9. IF a `POST /api/me/planner/completed` request is missing required fields or contains
   out-of-range values, THEN THE PlannerService SHALL return HTTP 400 indicating which fields are
   invalid.

---

### Requirement 9: AI-Assisted Course Suggestions

**Phase: 2**

**User Story:** As a student, I want the assistant to recommend which courses to take next based
on my completed modules and the module catalog, so that I can plan my remaining semesters
efficiently.

#### Acceptance Criteria

1. WHEN an authenticated student calls `GET /api/me/planner/suggestions`, THE PlannerService SHALL
   return a list of `CourseSuggestionDto` objects.
2. THE PlannerService SHALL call `ChunkSearch` with a query derived from the student's degree
   program and completed modules to retrieve relevant module catalog chunks from the RAG index.
3. THE PlannerService SHALL pass the retrieved chunks and the student's completed modules to
   Claude to generate ranked course recommendations.
4. WHERE a live course portal connection is unavailable (Phase 2 upload-bridge mode), THE
   PlannerService SHALL base suggestions solely on the RAG index and the uploaded eCampus data,
   and SHALL include `"liveDataAvailable": false` in the response.

---

### Requirement 10: Wellness and Lifestyle Reminders

**Phase: 3**

**User Story:** As a student who opts in, I want the assistant to send me short, personalized
wellness nudges (hydration, breaks, sleep, exercise, meals) at appropriate times, so that I
maintain healthy habits during study periods.

#### Acceptance Criteria

1. THE WellnessReminderService SHALL evaluate all opted-in students on a fixed interval of every
   30 minutes and enqueue reminders only for students who pass all configured `WellnessRule`
   checks.
2. THE WellnessReminderService SHALL apply the following built-in rules in AND logic — a single
   blocking rule is sufficient to suppress the nudge:
   - `OptInRule`: blocks if the `wellness_enabled` preference is not `true`.
   - `QuietHoursRule`: blocks if the current time (in the university's configured time zone) lies
     inside the student's quiet-hours window, from `quietHoursStart` (inclusive) to
     `quietHoursEnd` (exclusive). The window MAY cross midnight (e.g. 22:00–08:00).
   - `CooldownRule`: blocks if a notification of the same type was dispatched within the
     effective cooldown duration.
   - `ExamPeriodRule`: does not block on its own; WHEN an exam DEADLINE notification exists for
     the student within the next 72 hours, it doubles the effective cooldown used by
     `CooldownRule`.
3. WHEN all rules pass, THE WellnessReminderService SHALL call Claude with a prompt containing
   the nudge type (HYDRATE, BREAK, EXERCISE, EAT, SLEEP), time of day, and the student's first
   name (no other personal data), and SHALL instruct Claude to produce a message of at most 40
   words.
4. WHEN a nudge is generated successfully, THE WellnessReminderService SHALL store it in
   `notification_log` with type `WELLNESS`.
5. IF the Claude API call for nudge generation fails or times out, THEN THE WellnessReminderService
   SHALL skip that nudge for the current cycle, store nothing in `notification_log`, and log the
   failure at WARN level; no retry SHALL be attempted until the next scheduled 30-minute cycle.
6. WHEN an authenticated student calls `PUT /api/me/wellness/settings` with `enabled` (boolean),
   `quietHoursStart` (HH:mm, 00:00–23:59), and `quietHoursEnd` (HH:mm, 00:00–23:59, must differ
   from `quietHoursStart`; may be earlier to describe a window across midnight), THE System SHALL
   persist the preferences and return HTTP 204.
7. IF the `PUT /api/me/wellness/settings` request contains an invalid time format, an out-of-range
   time, or a `quietHoursEnd` equal to `quietHoursStart`, THEN THE System SHALL return HTTP 422
   with error code `invalid_wellness_settings` without modifying any existing preferences.
8. WHEN an authenticated student calls `GET /api/me/wellness/notifications?limit=N` where N is
   between 1 and 100, THE System SHALL return the N most recent `WELLNESS` type notifications for
   that student, ordered by `created_at` descending.
9. IF N in `GET /api/me/wellness/notifications?limit=N` is outside the range 1–100, THEN THE
   System SHALL return HTTP 422 with error code `invalid_limit`.

---

### Requirement 11: Campus Event Suggestions

**Phase: 3**

**User Story:** As a student, I want the assistant to surface upcoming campus events and student
union activities that are relevant to my degree program, so that I can stay engaged with campus
life.

#### Acceptance Criteria

1. THE WellnessReminderService SHALL execute a `ChunkSearch` for terms configured in
   `assistant.university.event-search-terms` (e.g. `Veranstaltung`, `event`, `AStA`) once per day.
2. THE WellnessReminderService SHALL pass the top-5 search results to Claude and request a brief
   summary tailored to the student's degree program.
3. THE System SHALL store the resulting event suggestion in `notification_log` with type `EVENT`.

---

### Requirement 12: University Configuration

**Phase: 1**

**User Story:** As a system operator deploying this assistant for a new university, I want all
institution-specific values to be controlled entirely by YAML configuration, so that I can onboard
a second university without changing any application code.

#### Acceptance Criteria

1. THE System SHALL read all university-specific values from the `assistant.university.*` YAML
   namespace via a `UniversityProperties` configuration record; no university-specific values SHALL
   be hard-coded in application logic.
2. THE System SHALL support at minimum the following `assistant.university.*` configuration keys:
   `name`, `short-name`, `email-domain-pattern`, `locale`, `grade-scale`, `term-format`,
   `portal-type`, `portal-base-url`, `sso-type`, `sso-issuer-url`, and `event-search-terms`.
3. WHEN `assistant.university.email-domain-pattern` is changed in the YAML profile, THE AuthFilter
   SHALL apply the new pattern to all subsequent registration requests without requiring a code
   change or recompilation.
4. THE System SHALL support the following enumerated values for `portal-type`:
   `ECAMPUS_UPLOAD` (Phase 2 upload bridge), `ECAMPUS_SESSION` (Phase 3 session client), and
   `CUSTOM_REST`.
5. THE System SHALL support the following enumerated values for `grade-scale`:
   `GERMAN_1_TO_5`, `US_LETTER`, `PERCENTAGE`, and `CUSTOM`.
6. IF any required `assistant.university.*` configuration key is absent or empty at application
   startup, THEN THE System SHALL fail to start and log a FATAL-level message identifying each
   missing key by name.
7. IF `assistant.university.portal-type` is set to a value other than `ECAMPUS_UPLOAD`,
   `ECAMPUS_SESSION`, or `CUSTOM_REST`, THEN THE System SHALL fail to start and log a FATAL-level
   message stating the invalid value and the accepted values.
8. IF `assistant.university.grade-scale` is set to a value other than `GERMAN_1_TO_5`,
   `US_LETTER`, `PERCENTAGE`, or `CUSTOM`, THEN THE System SHALL fail to start and log a
   FATAL-level message stating the invalid value and the accepted values.

---

### Requirement 13: Security Hardening

**Phase: 1**

**User Story:** As a system operator, I want all sensitive credentials, tokens, and student data
to be properly protected, so that the system meets university data-protection requirements.

#### Acceptance Criteria

1. THE System SHALL store student passwords exclusively as bcrypt hashes with cost factor 12; plain-text
   passwords SHALL never be logged, stored, or returned in any API response.
2. THE System SHALL never log, return in API responses, or persist in plaintext any OAuth
   `access_token` or `refresh_token` value.
3. THE System SHALL never log, return in API responses, or store student portal passwords;
   Phase 3 SSO session tokens SHALL not be persisted beyond the duration of the consented action.
4. IF the `ACCOUNT_ENCRYPTION_KEY` environment variable is absent at startup, THEN THE System
   SHALL fail to start and log a `FATAL`-level message identifying the missing variable.
5. THE System SHALL never return raw error messages, stack traces, or internal identifiers from
   Claude, the email provider, or the database to API clients; all unexpected errors SHALL be
   mapped to generic error codes (`internal_error`, `ai_unavailable`, `ai_bad_response`).
6. WHEN eCampus session-based registration (Phase 3) is used, THE System SHALL require explicit
   in-UI confirmation from the student before sending any mutating request to the eCampus portal;
   a session token SHALL not be persisted after the action completes.
7. THE System SHALL apply a 10-second network timeout to all outbound calls to the eCampus portal
   (Phase 3) and a 60-second timeout to all Claude API calls.
8. THE System SHALL delete `question` rows (and their `feedback`) older than 12 months once per
   day, for both anonymous and personalized questions (see `docs/decisions.md` #6 and #8).
9. THE Phase 3 eCampus session client (Requirement 13.6) SHALL NOT be built unless the
   university provides an official API or written approval; until then `portal-type` stays
   `ECAMPUS_UPLOAD` (see `docs/decisions.md` #3).

---

### Requirement 14: One Assistant, Specialist Agents

**Phase: 4 — criteria 1–7 built before accounts; 8–12 need login (and, where noted, university approval)**

**User Story:** As a student, I want one assistant that hands my requests to specialists for
deadlines, onboarding, campus life and regulations, so that it can act on my behalf where that is
safe and answer from the right source otherwise.

#### Acceptance Criteria

1. WHEN a student sends a chat message to `POST /api/assistant`, THE Concierge SHALL hand it to
   the specialist agent with the highest relevance at or above 0.5, and otherwise to the
   regulations agent, which answers from the crawled pages with sources.
2. EACH reply SHALL name the agent that produced it and MAY contain cards (dates, events, steps)
   and actions (save reminder, add to calendar, open checklist, follow-up question).
3. AGENTS SHALL only suggest actions; THE System SHALL NOT carry out an action until the student
   taps it.
4. WHEN a student asks to be reminded of something, THE Deadline agent SHALL read the day and time
   (English or German, e.g. "Friday at 10", "übermorgen um 14 Uhr", "am 20.10.") and offer to save
   the reminder; IF no day is given or the time has passed, THEN it SHALL ask again instead of
   guessing.
5. WHEN a student asks what is coming up, THE Deadline agent SHALL list their reminders and the
   upcoming key dates, nearest first.
6. WHEN a student asks about their checklist, THE Onboarding agent SHALL report progress and the
   next open steps of their active journey.
7. WHEN a student asks what is on (today, tomorrow, this weekend, this or next week), THE Campus
   agent SHALL list matching events from their department and the university.
8. *(login)* Reminders, journey progress and agent settings SHALL be stored per student on the
   server instead of in the browser.
9. *(login)* Agents SHALL run in the background on a schedule or on events (new feed items, dates
   approaching) and deliver results as notifications even when the page is closed.
10. *(login)* Students SHALL be able to switch each agent on or off and see which data it may use;
    an agent SHALL only access the data it needs.
11. *(login + ITS approval)* THE Inbox agent SHALL summarise university email (Requirements 4–5).
12. *(login + eCampus export)* THE Study planner agent SHALL plan modules from the uploaded
    transcript and the module database (Requirements 7–9).

