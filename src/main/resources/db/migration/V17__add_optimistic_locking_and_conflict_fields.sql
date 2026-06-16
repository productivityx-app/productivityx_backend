-- V16__add_optimistic_locking_and_conflict_fields.sql
--
-- B-0.1: Adds jpa_version to all mutable entities for JPA optimistic locking.
--        Spring Data JPA @Version reads this column on every UPDATE and includes
--        WHERE jpa_version = ? — preventing silent concurrent overwrites.
--
-- B-0.2: Ensures notes.version column exists (was already there per schema) and
--        adds an index for efficient conflict queries.
--
-- All columns default to 0 so existing rows are immediately valid.

-- Notes
ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

-- Tasks
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

-- Events
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

-- Pomodoro sessions
ALTER TABLE pomodoro_sessions
    ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

-- Conversations
ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS jpa_version BIGINT NOT NULL DEFAULT 0;

-- Index on (user_id, updated_at, id) for efficient cursor-based delta sync (B-0.4)
-- Covering index — avoids heap fetch for the cursor comparison columns.
CREATE INDEX IF NOT EXISTS idx_notes_cursor
    ON notes (user_id, updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_tasks_cursor
    ON tasks (user_id, updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_events_cursor
    ON events (user_id, updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_pomodoro_cursor
    ON pomodoro_sessions (user_id, created_at ASC, id ASC);
