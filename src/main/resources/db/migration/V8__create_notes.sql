CREATE TABLE notes (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                VARCHAR(500) NOT NULL DEFAULT '',
    content              TEXT         NOT NULL DEFAULT '',
    plain_text_content   TEXT         NOT NULL DEFAULT '',
    word_count           INT          NOT NULL DEFAULT 0,
    reading_time_seconds INT          NOT NULL DEFAULT 0,
    is_pinned            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    version              INT          NOT NULL DEFAULT 1,
    sync_status          VARCHAR(10)  NOT NULL DEFAULT 'SYNCED',
    search_vector        TSVECTOR,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notes_user_id      ON notes (user_id);
CREATE INDEX idx_notes_user_deleted ON notes (user_id, is_deleted);
CREATE INDEX idx_notes_updated_at   ON notes (user_id, updated_at DESC);
CREATE INDEX idx_notes_sync_status  ON notes (user_id, sync_status);
CREATE INDEX idx_notes_search       ON notes USING GIN (search_vector);

-- Auto-update search_vector on insert/update
CREATE OR REPLACE FUNCTION update_note_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.plain_text_content, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notes_search_vector
    BEFORE INSERT OR UPDATE ON notes
    FOR EACH ROW EXECUTE FUNCTION update_note_search_vector();

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notes_updated_at
    BEFORE UPDATE ON notes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
