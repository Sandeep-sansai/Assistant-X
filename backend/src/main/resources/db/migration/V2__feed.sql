-- Items read from the university's news and event listing pages (see FeedService).
CREATE TABLE feed_item (
    id            BIGSERIAL   PRIMARY KEY,
    source_id     TEXT        NOT NULL,             -- assistant.feed.sources[].id
    department    TEXT,                             -- department id, NULL = university-wide
    kind          TEXT        NOT NULL,             -- 'NEWS' | 'EVENT'
    title         TEXT        NOT NULL,
    url           TEXT        NOT NULL,
    category      TEXT,                             -- the listing's own label, e.g. 'ECE'
    item_date     DATE        NOT NULL,             -- event date, or publication date for news
    first_seen_at TIMESTAMPTZ NOT NULL,             -- when the feed reader first saw it ("new" badge)
    last_seen_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, url)
);
CREATE INDEX feed_item_kind_date_idx ON feed_item (kind, item_date);
