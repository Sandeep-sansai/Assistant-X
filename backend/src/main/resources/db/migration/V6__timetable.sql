-- Weekly course times from the eCampus course catalogue (imported with the IT service's permission,
-- docs/decisions.md #20) or entered by hand. One row per recurring slot.
CREATE TABLE course_slot (
    id           BIGSERIAL   PRIMARY KEY,
    programme_id TEXT        NOT NULL,             -- e.g. 'cs-msc-2026'
    term         TEXT        NOT NULL,             -- 'WS2026', 'SS2027'
    course_title TEXT        NOT NULL,             -- as the catalogue names it
    module_code  TEXT,                             -- matched planner module, null if none matched
    kind         TEXT,                             -- lecture, exercise, lab, seminar, …
    weekday      SMALLINT    NOT NULL CHECK (weekday BETWEEN 1 AND 7),  -- ISO: 1 = Monday
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    room         TEXT,
    source_url   TEXT,
    imported_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);
CREATE INDEX course_slot_programme_term_idx ON course_slot (programme_id, term);
