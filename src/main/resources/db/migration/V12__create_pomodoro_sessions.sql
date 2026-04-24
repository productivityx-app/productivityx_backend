CREATE TABLE pomodoro_sessions (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id                     UUID        REFERENCES tasks (id) ON DELETE SET NULL,
    type                        VARCHAR(15) NOT NULL,
    planned_duration_seconds    INT         NOT NULL,
    actual_duration_seconds     INT,
    interrupted                 BOOLEAN     NOT NULL DEFAULT FALSE,
    interrupt_reason            VARCHAR(255),
    focus_minutes_setting       INT         NOT NULL,
    short_break_minutes_setting INT         NOT NULL,
    long_break_minutes_setting  INT         NOT NULL,
    started_at                  TIMESTAMPTZ NOT NULL,
    ended_at                    TIMESTAMPTZ,
    completed                   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pomodoro_user_id   ON pomodoro_sessions (user_id);
CREATE INDEX idx_pomodoro_task      ON pomodoro_sessions (task_id);
CREATE INDEX idx_pomodoro_user_date ON pomodoro_sessions (user_id, started_at DESC);
CREATE INDEX idx_pomodoro_user_type ON pomodoro_sessions (user_id, type);
CREATE INDEX idx_pomodoro_completed ON pomodoro_sessions (user_id, completed, started_at DESC);
