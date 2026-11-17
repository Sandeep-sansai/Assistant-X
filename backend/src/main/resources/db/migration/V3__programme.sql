-- Degree programmes imported from the university's module database (see ProgrammeService).
-- One JSON snapshot per programme: areas, focus options, modules and the rules a plan depends on.
CREATE TABLE programme_snapshot (
    id          TEXT        PRIMARY KEY,           -- assistant.programmes[].id, e.g. 'cs-msc-2026'
    source_url  TEXT        NOT NULL,
    fetched_at  TIMESTAMPTZ NOT NULL,
    data        JSONB       NOT NULL
);
