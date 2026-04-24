-- updated_at triggers for tables created before set_updated_at() was defined (V5/V6)
-- These are safe to run here — Flyway guarantees sequential execution.
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_prefs_updated_at
    BEFORE UPDATE ON user_preferences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
