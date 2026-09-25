CREATE TABLE fanfics (
    id              UUID PRIMARY KEY,
    source_url      VARCHAR(2048)   NOT NULL,
    external_id     VARCHAR(64)     NOT NULL UNIQUE,
    title           VARCHAR(1024),
    description     TEXT,
    rating          VARCHAR(32),
    language        VARCHAR(64),
    genre           VARCHAR(128),
    characters      VARCHAR(512),
    chapters_count  INTEGER,
    words           INTEGER,
    reviews         INTEGER,
    favs            INTEGER,
    follows         INTEGER,
    published_at    TIMESTAMPTZ,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    pdf_path        VARCHAR(2048),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_fanfics_status ON fanfics (status);
CREATE INDEX idx_fanfics_created_at ON fanfics (created_at);

CREATE TABLE chapters (
    id                    UUID PRIMARY KEY,
    fanfic_id             UUID        NOT NULL REFERENCES fanfics (id) ON DELETE CASCADE,
    chapter_number        INTEGER     NOT NULL,
    title                 VARCHAR(512),
    raw_html              TEXT,
    extraction_file_name  VARCHAR(512),
    CONSTRAINT uq_chapter_fanfic_number UNIQUE (fanfic_id, chapter_number)
);

CREATE INDEX idx_chapters_fanfic_id ON chapters (fanfic_id);
