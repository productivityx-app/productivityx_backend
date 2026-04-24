CREATE TABLE tasks (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_task_id       UUID         REFERENCES tasks (id) ON DELETE CASCADE,
    -- linked_event_id FK added after events table is created in V11
    linked_event_id      UUID,
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    status               VARCHAR(15)  NOT NULL DEFAULT 'TODO',
    priority             VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    due_date             DATE,
    due_time             TIME,
    reminder_at          TIMESTAMPTZ,
    estimated_minutes    INT,
    actual_minutes       INT          NOT NULL DEFAULT 0,
    completed_at         TIMESTAMPTZ,
    position             INT          NOT NULL DEFAULT 0,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    version              INT          NOT NULL DEFAULT 1,
    sync_status          VARCHAR(10)  NOT NULL DEFAULT 'SYNCED',
    search_vector        TSVECTOR,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_user_id      ON tasks (user_id);
CREATE INDEX idx_tasks_parent       ON tasks (parent_task_id);
CREATE INDEX idx_tasks_user_status  ON tasks (user_id, status);
CREATE INDEX idx_tasks_user_due     ON tasks (user_id, due_date);
CREATE INDEX idx_tasks_user_deleted ON tasks (user_id, is_deleted);
CREATE INDEX idx_tasks_updated_at   ON tasks (user_id, updated_at);
CREATE INDEX idx_tasks_sync_status  ON tasks (user_id, sync_status);
CREATE INDEX idx_tasks_search       ON tasks USING GIN (search_vector);

-- Auto-update search_vector
CREATE OR REPLACE FUNCTION update_task_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tasks_search_vector
    BEFORE INSERT OR UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_task_search_vector();

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
