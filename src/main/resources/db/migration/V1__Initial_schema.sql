-- =============================================
-- V1__Initial_schema.sql - FINAL FIXED VERSION
-- =============================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- =============================================
-- USERS
-- =============================================
CREATE TABLE users (
                       id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email               VARCHAR(255) NOT NULL UNIQUE,
                       username            VARCHAR(30) UNIQUE,
                       phone               VARCHAR(20) UNIQUE,
                       password_hash       VARCHAR(255) NOT NULL,
                       is_email_verified   BOOLEAN NOT NULL DEFAULT false,
                       is_active           BOOLEAN NOT NULL DEFAULT true,
                       gender              VARCHAR(10),                    -- VARCHAR instead of ENUM
                       birth_date          DATE,
                       last_login_at       TIMESTAMPTZ,
                       failed_login_count  INTEGER NOT NULL DEFAULT 0,
                       locked_until        TIMESTAMPTZ,
                       created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- PROFILES & PREFERENCES
-- =============================================
CREATE TABLE profiles (
                          id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id       UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                          first_name    VARCHAR(50) NOT NULL,
                          last_name     VARCHAR(50) NOT NULL,
                          avatar_url    TEXT,
                          bio           VARCHAR(500),
                          timezone      VARCHAR(50) NOT NULL DEFAULT 'UTC',
                          language      VARCHAR(10) NOT NULL DEFAULT 'EN',     -- VARCHAR
                          theme         VARCHAR(10) NOT NULL DEFAULT 'DARK',   -- VARCHAR
                          created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_preferences (
                                  id                                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id                            UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                                  pomodoro_focus_minutes             INTEGER NOT NULL DEFAULT 25,
                                  pomodoro_short_break_minutes       INTEGER NOT NULL DEFAULT 5,
                                  pomodoro_long_break_minutes        INTEGER NOT NULL DEFAULT 15,
                                  pomodoro_cycles_before_long_break  INTEGER NOT NULL DEFAULT 4,
                                  pomodoro_auto_start_breaks         BOOLEAN NOT NULL DEFAULT false,
                                  pomodoro_auto_start_focus          BOOLEAN NOT NULL DEFAULT false,
                                  pomodoro_sound_enabled             BOOLEAN NOT NULL DEFAULT true,
                                  notify_task_reminders              BOOLEAN NOT NULL DEFAULT true,
                                  notify_event_reminders             BOOLEAN NOT NULL DEFAULT true,
                                  notify_pomodoro_end                BOOLEAN NOT NULL DEFAULT true,
                                  notify_daily_summary               BOOLEAN NOT NULL DEFAULT false,
                                  default_task_view                  VARCHAR(10) NOT NULL DEFAULT 'LIST',
                                  default_task_sort                  VARCHAR(20) NOT NULL DEFAULT 'DUE_DATE',
                                  show_completed_tasks               BOOLEAN NOT NULL DEFAULT false,
                                  default_calendar_view              VARCHAR(10) NOT NULL DEFAULT 'WEEK',
                                  week_starts_on                     VARCHAR(3) NOT NULL DEFAULT 'MON',
                                  ai_context_enabled                 BOOLEAN NOT NULL DEFAULT true,
                                  ai_model                           VARCHAR(50) NOT NULL DEFAULT 'gemini-2.0-flash',
                                  compact_mode                       BOOLEAN NOT NULL DEFAULT false,
                                  created_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  updated_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- TAGS + NOTES
-- =============================================
CREATE TABLE tags (
                      id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                      name       VARCHAR(50) NOT NULL,
                      color      VARCHAR(7) NOT NULL DEFAULT '#6366F1',
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      UNIQUE (user_id, name)
);

CREATE TABLE notes (
                       id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       title               VARCHAR(255),
                       content             TEXT,
                       plain_text_content  TEXT,
                       search_vector       tsvector,
                       is_pinned           BOOLEAN NOT NULL DEFAULT false,
                       is_deleted          BOOLEAN NOT NULL DEFAULT false,
                       deleted_at          TIMESTAMPTZ,
                       created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE note_tags (
                           note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                           tag_id  UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                           PRIMARY KEY (note_id, tag_id)
);

-- =============================================
-- EVENTS
-- =============================================
CREATE TABLE events (
                        id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        recurrence_parent_id UUID REFERENCES events(id) ON DELETE SET NULL,
                        title               VARCHAR(500) NOT NULL,
                        description         TEXT,
                        location            VARCHAR(255),
                        start_at            TIMESTAMPTZ NOT NULL,
                        end_at              TIMESTAMPTZ NOT NULL,
                        is_all_day          BOOLEAN NOT NULL DEFAULT false,
                        color               VARCHAR(7) NOT NULL DEFAULT '#6366F1',
                        recurrence_rule     VARCHAR(255),
                        recurrence_end_at   TIMESTAMPTZ,
                        reminder_minutes    INTEGER,
                        is_deleted          BOOLEAN NOT NULL DEFAULT false,
                        deleted_at          TIMESTAMPTZ,
                        version             INTEGER NOT NULL DEFAULT 1,
                        created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- TASKS
-- =============================================
CREATE TABLE tasks (
                       id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       parent_task_id    UUID REFERENCES tasks(id) ON DELETE SET NULL,
                       linked_event_id   UUID REFERENCES events(id) ON DELETE SET NULL,
                       title             VARCHAR(500) NOT NULL,
                       description       TEXT,
                       status            VARCHAR(15) NOT NULL DEFAULT 'TODO',      -- VARCHAR
                       priority          VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',    -- VARCHAR
                       due_date          DATE,
                       due_time          TIME,
                       reminder_at       TIMESTAMPTZ,
                       estimated_minutes INTEGER,
                       actual_minutes    INTEGER NOT NULL DEFAULT 0,
                       completed_at      TIMESTAMPTZ,
                       position          INTEGER NOT NULL DEFAULT 0,
                       is_deleted        BOOLEAN NOT NULL DEFAULT false,
                       deleted_at        TIMESTAMPTZ,
                       version           INTEGER NOT NULL DEFAULT 1,
                       search_vector     tsvector,
                       created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- POMODORO + AUTH + AI
-- =============================================
CREATE TABLE pomodoro_sessions (
                                   id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   user_id                      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   task_id                      UUID REFERENCES tasks(id) ON DELETE SET NULL,
                                   type                         VARCHAR(15) NOT NULL,               -- VARCHAR
                                   planned_duration_seconds     INTEGER NOT NULL,
                                   actual_duration_seconds      INTEGER,
                                   interrupted                  BOOLEAN NOT NULL DEFAULT false,
                                   interrupt_reason             VARCHAR(255),
                                   focus_minutes_setting        INTEGER NOT NULL,
                                   short_break_minutes_setting  INTEGER NOT NULL,
                                   long_break_minutes_setting   INTEGER NOT NULL,
                                   started_at                   TIMESTAMPTZ NOT NULL,
                                   ended_at                     TIMESTAMPTZ,
                                   completed                    BOOLEAN NOT NULL DEFAULT false,
                                   created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
                                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash   VARCHAR(255) NOT NULL UNIQUE,
                                device_info  VARCHAR(255),
                                ip_address   VARCHAR(45),
                                expires_at   TIMESTAMPTZ NOT NULL,
                                revoked_at   TIMESTAMPTZ,
                                created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE password_reset_tokens (
                                       id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       token_hash VARCHAR(255) NOT NULL UNIQUE,
                                       expires_at TIMESTAMPTZ NOT NULL,
                                       used_at    TIMESTAMPTZ,
                                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE email_verification_tokens (
                                           id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                           user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           token_hash VARCHAR(255) NOT NULL UNIQUE,
                                           otp        VARCHAR(6) NOT NULL,
                                           expires_at TIMESTAMPTZ NOT NULL,
                                           used_at    TIMESTAMPTZ,
                                           created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE conversations (
                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               title       VARCHAR(255),
                               is_archived BOOLEAN NOT NULL DEFAULT false,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE messages (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                          role            VARCHAR(10) NOT NULL,                    -- VARCHAR
                          content         TEXT NOT NULL,
                          action_block    JSONB,
                          token_count     INTEGER,
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- INDEXES + SEARCH
-- =============================================
CREATE INDEX idx_notes_user_deleted ON notes(user_id, is_deleted);
CREATE INDEX idx_notes_search       ON notes USING GIN(search_vector);
CREATE INDEX idx_tasks_user_deleted ON tasks(user_id, is_deleted);
CREATE INDEX idx_tasks_search       ON tasks USING GIN(search_vector);
CREATE INDEX idx_events_user_start  ON events(user_id, start_at);
CREATE INDEX idx_conversations_user ON conversations(user_id, updated_at DESC);

-- Search triggers
CREATE OR REPLACE FUNCTION update_note_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
                         setweight(to_tsvector('english', COALESCE(NEW.plain_text_content, '')), 'B');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notes_search BEFORE INSERT OR UPDATE ON notes
                                                     FOR EACH ROW EXECUTE FUNCTION update_note_search_vector();

CREATE OR REPLACE FUNCTION update_task_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
                         setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tasks_search BEFORE INSERT OR UPDATE ON tasks
                                                     FOR EACH ROW EXECUTE FUNCTION update_task_search_vector();