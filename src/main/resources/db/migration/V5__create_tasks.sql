-- ═══════════════════════════════════════════════════════
-- TASKS
-- Self-referencing for subtasks (max 2 levels enforced in service layer).
-- linked_event_id added as a plain column here; FK to events added in
-- V11 after the events table exists (avoids forward-reference).
-- ═══════════════════════════════════════════════════════
CREATE TABLE tasks (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    parent_task_id       UUID                     REFERENCES tasks(id) ON DELETE CASCADE,
    linked_event_id      UUID,
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    status               VARCHAR(15)  NOT NULL DEFAULT 'TODO',
    priority             VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    due_date             DATE,
    due_time             TIME,
    reminder_at          TIMESTAMP WITH TIME ZONE,
    estimated_minutes    INT,
    actual_minutes       INT          NOT NULL DEFAULT 0,
    completed_at         TIMESTAMP WITH TIME ZONE,
    position             INT          NOT NULL DEFAULT 0,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMP WITH TIME ZONE,
    version              INT          NOT NULL DEFAULT 1,
    sync_status          VARCHAR(10)  NOT NULL DEFAULT 'SYNCED',
    search_vector        TSVECTOR,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Standard access patterns
CREATE INDEX idx_tasks_user_id       ON tasks(user_id);
CREATE INDEX idx_tasks_parent        ON tasks(parent_task_id);
CREATE INDEX idx_tasks_user_status   ON tasks(user_id, status)    WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_user_due      ON tasks(user_id, due_date)  WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_user_deleted  ON tasks(user_id, is_deleted);
CREATE INDEX idx_tasks_updated_at    ON tasks(user_id, updated_at DESC);
CREATE INDEX idx_tasks_sync_status   ON tasks(user_id, sync_status);

-- GIN index for full-text search
CREATE INDEX idx_tasks_search        ON tasks USING GIN(search_vector);

-- ═══════════════════════════════════════════════════════
-- TRIGGERS
-- ═══════════════════════════════════════════════════════

-- Maintain updated_at automatically — same pattern as notes
CREATE OR REPLACE FUNCTION set_tasks_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION set_tasks_updated_at();

-- Maintain tsvector for full-text search — title weighted A, description weighted B
CREATE OR REPLACE FUNCTION update_task_search_vector() RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_search_vector
    BEFORE INSERT OR UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_task_search_vector();
