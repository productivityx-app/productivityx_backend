ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS reading_time_seconds INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS plain_text_content   TEXT        NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS word_count           INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sync_status          VARCHAR(10) NOT NULL DEFAULT 'SYNCED',
    ADD COLUMN IF NOT EXISTS version              INT         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_pinned            BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at           TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_notes_user_deleted  ON notes(user_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_notes_updated_at    ON notes(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_sync_status   ON notes(user_id, sync_status);