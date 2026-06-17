package com.oussama_chatri.productivityx.features.events.repository;

import com.oussama_chatri.productivityx.features.events.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND e.startAt < :to
              AND e.endAt > :from
            ORDER BY e.startAt ASC
            """)
    List<Event> findActiveByUserIdAndRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
            ORDER BY e.startAt ASC
            """)
    Page<Event> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = true
            ORDER BY e.deletedAt DESC
            """)
    Page<Event> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.userId = :userId")
    Optional<Event> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT e FROM Event e
            WHERE e.recurrenceParent.id = :parentId
              AND e.deleted = false
            ORDER BY e.startAt ASC
            """)
    List<Event> findActiveInstancesByParentId(@Param("parentId") UUID parentId);

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId AND e.updatedAt > :since
            ORDER BY e.updatedAt ASC
            """)
    List<Event> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * Cursor-based delta sync for events.
     * Stable pagination over updated_at + id for consistent sync pages.
     *
     * <p>Uses {@code CAST(... AS TEXT)} instead of {@code ::text} — Hibernate native
     * query parser incorrectly treats {@code :param::text} as a single parameter.
     */
    @Query(value = """
            SELECT e.* FROM events e
            WHERE e.user_id = :userId
              AND e.updated_at >= :since
              AND (e.updated_at > :cursorUpdatedAt
                   OR (e.updated_at = :cursorUpdatedAt AND CAST(e.id AS TEXT) > CAST(:cursorId AS TEXT)))
            ORDER BY e.updated_at ASC, e.id ASC
            LIMIT :limitVal
            """, nativeQuery = true)
    List<Event> findChangedSinceCursor(
            @Param("userId") UUID userId,
            @Param("since") Instant since,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limitVal") int limitVal);

    @Query("""
            SELECT COUNT(e) FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND e.startAt >= :from
              AND e.startAt < :to
            """)
    long countUpcomingByUserIdAndRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND e.startAt >= :dayStart
              AND e.startAt < :dayEnd
            ORDER BY e.startAt ASC
            """)
    List<Event> findTodayByUserId(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND (LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY e.startAt ASC
            """)
    Page<Event> searchFallback(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Event e WHERE e.deleted = true AND e.deletedAt < :cutoff")
    int purgeTrash(@Param("cutoff") Instant cutoff);

    /**
     * Hard-deletes up to 500 trashed events older than the cutoff, returning the
     * purged (id, user_id) pairs. Used by CleanupScheduler for batch processing
     * — call in a loop until zero rows are returned.
     *
     * <p>Uses a CTE with LIMIT to cap each DELETE to a predictable batch size,
     * preventing long-running transactions on large trash backlogs.
     */
    @Query(value = """
            WITH batch AS (
                SELECT id FROM events
                WHERE deleted = true AND deleted_at < :cutoff
                LIMIT 500
            )
            DELETE FROM events WHERE id IN (SELECT id FROM batch)
            RETURNING id, user_id
            """, nativeQuery = true)
    List<Object[]> purgeTrashReturning(@Param("cutoff") Instant cutoff);
}
