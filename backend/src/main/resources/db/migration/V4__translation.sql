-- English versions of German titles from university pages (module names, areas, news headlines),
-- made once by the language model and reused (see Translator).
CREATE TABLE translation (
    source      TEXT        NOT NULL,                -- the original German text
    lang        TEXT        NOT NULL,                -- target language, 'en'
    text        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (source, lang)
);
