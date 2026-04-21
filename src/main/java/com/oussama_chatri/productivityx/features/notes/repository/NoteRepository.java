package com.oussama_chatri.productivityx.features.notes.repository;

import com.oussama_chatri.productivityx.features.notes.entity.Note;
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
public interface NoteRepository extends JpaRepository<Note, UUID> {

    // Active notes (not deleted) — primary listing endpoint
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = false
            ORDER BY n.pinned DESC, n.updatedAt DESC
            """)
    Page<Note> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Filtered by tag
    @Query("""
            SELECT DISTINCT n FROM Note n
            LEFT JOIN FETCH n.tags t
            WHERE n.userId = :userId
              AND n.deleted = false
              AND t.id = :tagId
            ORDER BY n.pinned DESC, n.updatedAt DESC
            """)
    Page<Note> findActiveByUserIdAndTagId(
            @Param("userId") UUID userId,
            @Param("tagId") UUID tagId,
            Pageable pageable);

    // Pinned-only filter
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = false
              AND n.pinned = true
            ORDER BY n.updatedAt DESC
            """)
    Page<Note> findPinnedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Trash — soft-deleted notes
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = true
            ORDER BY n.deletedAt DESC
            """)
    Page<Note> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Single note — ownership-aware lookup
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.id = :id AND n.userId = :userId
            """)
    Optional<Note> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // Delta sync — all changes (including deleted) since a given timestamp
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId AND n.updatedAt > :since
            ORDER BY n.updatedAt ASC
            """)
    List<Note> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // Nightly trash purge — auto-delete notes trashed more than 30 days ago
    @Modifying
    @Query("DELETE FROM Note n WHERE n.deleted = true AND n.deletedAt < :cutoff")
    int purgeTrash(@Param("cutoff") Instant cutoff);

    // Count active notes (used by AI context builder and home dashboard)
    @Query("SELECT COUNT(n) FROM Note n WHERE n.userId = :userId AND n.deleted = false")
    long countActiveByUserId(@Param("userId") UUID userId);

    // Plain-text FTS fallback when PostgreSQL tsvector is unavailable (dev environment)
    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = false
              AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(n.plainTextContent) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY n.pinned DESC, n.updatedAt DESC
            """)
    Page<Note> searchFallback(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);
}