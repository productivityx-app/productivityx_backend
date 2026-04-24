CREATE TABLE profiles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    first_name VARCHAR(50) NOT NULL,
    last_name  VARCHAR(50) NOT NULL,
    avatar_url TEXT,
    bio        VARCHAR(500),
    timezone   VARCHAR(50) NOT NULL DEFAULT 'UTC',
    language   VARCHAR(10) NOT NULL DEFAULT 'EN',
    theme      VARCHAR(10) NOT NULL DEFAULT 'DARK',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_user_id ON profiles (user_id);
