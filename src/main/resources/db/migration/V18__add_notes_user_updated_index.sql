-- Index: speeds up note list queries ordered by updated_at DESC
-- Used by: findActiveByUserId, findActiveByUserIdAndTagId, delta sync
-- CREATE CONCURRENTLY avoids locking the table during creation (safe for production)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notes_user_updated ON notes(user_id, updated_at DESC);
