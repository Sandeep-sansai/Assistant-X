-- Free-text feedback from the page's "Send feedback" button while the app is in testing.
-- Nothing identifies the sender; the page says so next to the form.
CREATE TABLE site_feedback (
    id          BIGSERIAL   PRIMARY KEY,
    message     TEXT        NOT NULL,
    lang        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
