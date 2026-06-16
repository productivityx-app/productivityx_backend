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

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = false
            ORDER BY n.pinned DESC, n.updatedAt DESC
            """)
    Page<Note> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

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

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = false
              AND n.pinned = true
            ORDER BY n.updatedAt DESC
            """)
    Page<Note> findPinnedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId
              AND n.deleted = true
            ORDER BY n.deletedAt DESC
            """)
    Page<Note> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.id = :id AND n.userId = :userId
            """)
    Optional<Note> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.tags
            WHERE n.userId = :userId AND n.updatedAt > :since
            ORDER BY n.updatedAt ASC
            """)
    List<Note> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * Cursor-based delta sync for notes.
     * Returns paginated notes modified after cursor position, ordered by (updated_at, id).
     * Includes both active and deleted notes — clients filter by deleted flag.
     */
    @Query(value = """
            SELECT n.* FROM notes n
            WHERE n.user_id = :userId
              AND n.updated_at >= :since
              AND (n.updated_at > :cursorUpdatedAt
                   OR (n.updated_at = :cursorUpdatedAt AND n.id::text > :cursorId::text))
            ORDER BY n.updated_at ASC, n.id ASC
            LIMIT :limitVal
            """, nativeQuery = true)
    List<Note> findChangedSinceCursor(
            @Param("userId") UUID userId,
            @Param("since") Instant since,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limitVal") int limitVal);

    @Modifying
    @Query("DELETE FROM Note n WHERE n.deleted = true AND n.deletedAt < :cutoff")
    int purgeTrash(@Param("cutoff") Instant cutoff);

    /**
     * Hard-deletes up to 500 trashed notes older than the cutoff, returning the
     * purged (id, user_id) pairs. Used by CleanupScheduler for batch processing
     * — call in a loop until zero rows are returned.
     *
     * <p>Uses a CTE with LIMIT to cap each DELETE to a predictable batch size,
     * preventing long-running transactions on large trash backlogs.
     */
    @Query(value = """
            WITH batch AS (
                SELECT id FROM notes
                WHERE is_deleted = true AND deleted_at < :cutoff
                LIMIT 500
            )
            DELETE FROM notes WHERE id IN (SELECT id FROM batch)
            RETURNING id, user_id
            """, nativeQuery = true)
    List<Object[]> purgeTrashReturning(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(n) FROM Note n WHERE n.userId = :userId AND n.deleted = false")
    long countActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT n.title FROM Note n
            WHERE n.userId = :userId
              AND n.deleted = false
            ORDER BY n.updatedAt DESC
            LIMIT 1
            """)
    Optional<String> findLastEditedTitleByUserId(@Param("userId") UUID userId);

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
