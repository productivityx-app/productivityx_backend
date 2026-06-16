package com.oussama_chatri.productivityx.core.locking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PostgreSQL transaction-scoped advisory locks for concurrent entity editing.
 *
 * <p>Uses {@code pg_advisory_xact_lock(hashtext(key))} which guarantees:
 * <ul>
 *   <li>Lock is held for the duration of the current transaction only</li>
 *   <li>Lock releases automatically on commit or rollback — no unlock call needed</li>
 *   <li>Multiple locks per transaction are allowed (no self-deadlock)</li>
 *   <li>Blocks callers until the lock becomes available (no timeout / no polling)</li>
 * </ul>
 *
 * <p>Intended for the cross-device same-user race: a user with the app open on
 * both phone and laptop simultaneously. The advisory lock prevents the brief
 * read-then-write gap that JPA optimistic locking catches only after the fact.
 *
 * <p>Lock key format: {@code "{userId}:{entityId}"} — hashed via PostgreSQL
 * {@code hashtext()} for fast 64-bit lock ID generation. Using {@code hashtext}
 * instead of a raw 64-bit ID lets us keep the key namespace collision-free
 * across millions of (userId, entityId) pairs.
 */
@Slf4j
@Service
public class AdvisoryLockService {

    private static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtext(:key))";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Acquires a transaction-scoped advisory lock on a note.
     * Blocks until the lock is available. Lock auto-releases on TX end.
     */
    @Transactional
    public void acquireNoteLock(UUID userId, UUID noteId) {
        acquireLock(buildKey(userId, noteId), "note");
    }

    /**
     * Acquires a transaction-scoped advisory lock on a task.
     * Blocks until the lock is available. Lock auto-releases on TX end.
     */
    @Transactional
    public void acquireTaskLock(UUID userId, UUID taskId) {
        acquireLock(buildKey(userId, taskId), "task");
    }

    /**
     * Acquires a transaction-scoped advisory lock on an event.
     * Blocks until the lock is available. Lock auto-releases on TX end.
     */
    @Transactional
    public void acquireEventLock(UUID userId, UUID eventId) {
        acquireLock(buildKey(userId, eventId), "event");
    }

    private void acquireLock(String key, String entityType) {
        // pg_advisory_xact_lock is blocking and returns void —
        // execution resumes only after the lock is granted.
        Query query = entityManager.createNativeQuery(LOCK_SQL);
        query.setParameter("key", key);
        query.getSingleResult();

        log.debug("Advisory lock acquired type={} key={}", entityType, key);
    }

    private String buildKey(UUID userId, UUID entityId) {
        return userId + ":" + entityId;
    }
}
