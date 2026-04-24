CREATE TABLE events (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users  (id) ON DELETE CASCADE,
    recurrence_parent_id UUID         REFERENCES events (id) ON DELETE CASCADE,
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    location             VARCHAR(255),
    start_at             TIMESTAMPTZ  NOT NULL,
    end_at               TIMESTAMPTZ  NOT NULL,
    is_all_day           BOOLEAN      NOT NULL DEFAULT FALSE,
    color                VARCHAR(7)   NOT NULL DEFAULT '#6366F1',
    recurrence_rule      VARCHAR(255),
    recurrence_end_at    TIMESTAMPTZ,
    reminder_minutes     INT,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    version              INT          NOT NULL DEFAULT 1,
    sync_status          VARCHAR(10)  NOT NULL DEFAULT 'SYNCED',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_user_id      ON events (user_id);
CREATE INDEX idx_events_user_range   ON events (user_id, start_at, end_at);
CREATE INDEX idx_events_user_deleted ON events (user_id, is_deleted);
CREATE INDEX idx_events_recurrence   ON events (recurrence_parent_id);
CREATE INDEX idx_events_updated_at   ON events (user_id, updated_at);
CREATE INDEX idx_events_sync_status  ON events (user_id, sync_status);

CREATE TRIGGER trg_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Now that events table exists, add the FK on tasks.linked_event_id
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_linked_event
    FOREIGN KEY (linked_event_id) REFERENCES events (id) ON DELETE SET NULL;
