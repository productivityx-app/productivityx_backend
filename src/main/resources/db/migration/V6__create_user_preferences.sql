CREATE TABLE user_preferences (
    id                                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                           UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    pomodoro_focus_minutes            INT         NOT NULL DEFAULT 25,
    pomodoro_short_break_minutes      INT         NOT NULL DEFAULT 5,
    pomodoro_long_break_minutes       INT         NOT NULL DEFAULT 15,
    pomodoro_cycles_before_long_break INT         NOT NULL DEFAULT 4,
    pomodoro_auto_start_breaks        BOOLEAN     NOT NULL DEFAULT FALSE,
    pomodoro_auto_start_focus         BOOLEAN     NOT NULL DEFAULT FALSE,
    pomodoro_sound_enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_task_reminders             BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_event_reminders            BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_pomodoro_end               BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_daily_summary              BOOLEAN     NOT NULL DEFAULT FALSE,
    default_task_view                 VARCHAR(10) NOT NULL DEFAULT 'LIST',
    default_task_sort                 VARCHAR(20) NOT NULL DEFAULT 'DUE_DATE',
    show_completed_tasks              BOOLEAN     NOT NULL DEFAULT FALSE,
    default_calendar_view             VARCHAR(10) NOT NULL DEFAULT 'WEEK',
    week_starts_on                    VARCHAR(3)  NOT NULL DEFAULT 'MON',
    ai_context_enabled                BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_model                          VARCHAR(50) NOT NULL DEFAULT 'gemini-2.0-flash',
    compact_mode                      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_prefs_user_id ON user_preferences (user_id);
