CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL,
    content         TEXT        NOT NULL,
    action_block    JSONB,
    token_count     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_msg_conv_created ON messages (conversation_id, created_at);
