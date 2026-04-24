CREATE TABLE tags (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL DEFAULT '#6366F1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_tags_user_id ON tags (user_id);
