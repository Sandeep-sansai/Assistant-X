-- One row per crawled URL (HTML page or PDF).
CREATE TABLE document (
    id           BIGSERIAL PRIMARY KEY,
    url          TEXT        NOT NULL UNIQUE,
    title        TEXT        NOT NULL DEFAULT '',
    content_type TEXT        NOT NULL,                  -- 'html' | 'pdf'
    content_hash TEXT        NOT NULL,                  -- sha256 of the extracted text
    fetched_at   TIMESTAMPTZ NOT NULL,                  -- last time the crawler saw it
    changed_at   TIMESTAMPTZ NOT NULL,                  -- last time its text changed
    status       TEXT        NOT NULL DEFAULT 'active'  -- 'active' | 'gone'
);

-- Retrieval units. The heading path is stored with each chunk so a chunk makes sense on its own.
-- Pages are a mix of German and English, so each chunk is indexed with both stemmers.
CREATE TABLE chunk (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    ordinal     INT    NOT NULL,
    heading     TEXT   NOT NULL DEFAULT '',
    text        TEXT   NOT NULL,
    tsv_de      TSVECTOR GENERATED ALWAYS AS (
                    setweight(to_tsvector('german'::regconfig, heading), 'A') ||
                    setweight(to_tsvector('german'::regconfig, text), 'B')) STORED,
    tsv_en      TSVECTOR GENERATED ALWAYS AS (
                    setweight(to_tsvector('english'::regconfig, heading), 'A') ||
                    setweight(to_tsvector('english'::regconfig, text), 'B')) STORED,
    UNIQUE (document_id, ordinal)
);
CREATE INDEX chunk_tsv_de_idx ON chunk USING GIN (tsv_de);
CREATE INDEX chunk_tsv_en_idx ON chunk USING GIN (tsv_en);

CREATE TABLE ingestion_run (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    pages_fetched INT  NOT NULL DEFAULT 0,
    pages_changed INT  NOT NULL DEFAULT 0,
    errors        INT  NOT NULL DEFAULT 0,
    status        TEXT NOT NULL DEFAULT 'running'  -- 'running' | 'finished' | 'failed'
);

-- Anonymous question log: no user id, IP address or session is stored.
CREATE TABLE question (
    id          BIGSERIAL PRIMARY KEY,
    asked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    question    TEXT        NOT NULL,
    answer      TEXT        NOT NULL,
    had_sources BOOLEAN     NOT NULL,
    source_urls TEXT[]      NOT NULL DEFAULT '{}',
    top_score   REAL,
    model       TEXT,
    latency_ms  INT
);
CREATE INDEX question_asked_at_idx ON question (asked_at);

CREATE TABLE feedback (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT      NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL CHECK (rating IN (-1, 1)),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
