-- V2_add_otp_attempts_and_audit_log.sql
ALTER TABLE email_verification_tokens ADD COLUMN otp_attempts INT NOT NULL DEFAULT 0;

CREATE TABLE audit_logs (
                            id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
                            event       VARCHAR(40) NOT NULL,
                            ip_address  VARCHAR(45),
                            detail      VARCHAR(500),
                            created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_event      ON audit_logs(event);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);