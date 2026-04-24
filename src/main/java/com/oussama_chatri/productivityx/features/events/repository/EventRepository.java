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

    // Range query — primary calendar view
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

    // Paged list — used when no date range is provided
    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
            ORDER BY e.startAt ASC
            """)
    Page<Event> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Trash
    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = true
            ORDER BY e.deletedAt DESC
            """)
    Page<Event> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Ownership-aware single lookup
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.userId = :userId")
    Optional<Event> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // All instances of a recurring series
    @Query("""
            SELECT e FROM Event e
            WHERE e.recurrenceParent.id = :parentId
              AND e.deleted = false
            ORDER BY e.startAt ASC
            """)
    List<Event> findActiveInstancesByParentId(@Param("parentId") UUID parentId);

    // Delta sync support
    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId AND e.updatedAt > :since
            ORDER BY e.updatedAt ASC
            """)
    List<Event> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // AI context — upcoming events this week
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

    // Home dashboard — today's events
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

    // ILIKE fallback for dev environments without PostgreSQL FTS
    @Query("""
            SELECT e FROM Event e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND (LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY e.startAt ASC
            """)
    Page<Event> searchFallback(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);

    // Nightly trash purge
    @Modifying
    @Query("DELETE FROM Event e WHERE e.deleted = true AND e.deletedAt < :cutoff")
    int purgeTrash(@Param("cutoff") Instant cutoff);
}
