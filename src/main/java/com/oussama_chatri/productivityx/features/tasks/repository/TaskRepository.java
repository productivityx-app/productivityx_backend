package com.oussama_chatri.productivityx.features.tasks.repository;

import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.features.tasks.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    // Active tasks — primary listing endpoint (no subtasks fetched here, avoids pagination bug)
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.parentTask IS NULL
            ORDER BY t.position ASC, t.updatedAt DESC
            """)
    Page<Task> findTopLevelActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Filtered by status
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.parentTask IS NULL
              AND t.status = :status
            ORDER BY t.position ASC, t.updatedAt DESC
            """)
    Page<Task> findTopLevelActiveByUserIdAndStatus(
            @Param("userId") UUID userId,
            @Param("status") TaskStatus status,
            Pageable pageable);

    // Filtered by priority
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.parentTask IS NULL
              AND t.priority = :priority
            ORDER BY t.position ASC, t.updatedAt DESC
            """)
    Page<Task> findTopLevelActiveByUserIdAndPriority(
            @Param("userId") UUID userId,
            @Param("priority") TaskPriority priority,
            Pageable pageable);

    // Subtasks of a specific parent — List not Page (subtask count is always small)
    @Query("""
            SELECT t FROM Task t
            WHERE t.parentTask.id = :parentId
              AND t.deleted = false
            ORDER BY t.position ASC, t.createdAt ASC
            """)
    List<Task> findActiveSubtasksByParentId(@Param("parentId") UUID parentId);

    // Trash — soft-deleted top-level tasks only
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = true
              AND t.parentTask IS NULL
            ORDER BY t.deletedAt DESC
            """)
    Page<Task> findDeletedByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Ownership-aware single task lookup — includes subtasks via JOIN FETCH (safe for single result)
    @Query("""
            SELECT t FROM Task t
            LEFT JOIN FETCH t.subtasks s
            WHERE t.id = :id
              AND t.userId = :userId
            """)
    Optional<Task> findByIdAndUserIdWithSubtasks(@Param("id") UUID id, @Param("userId") UUID userId);

    // Lightweight ownership check — no subtasks fetched
    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.userId = :userId")
    Optional<Task> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // Delta sync — all changes (including deleted) since a given timestamp, ordered for client merge
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId AND t.updatedAt > :since
            ORDER BY t.updatedAt ASC
            """)
    List<Task> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // AI context builder and home dashboard counters
    @Query("SELECT COUNT(t) FROM Task t WHERE t.userId = :userId AND t.deleted = false AND t.parentTask IS NULL")
    long countActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.dueDate = :today
              AND t.status NOT IN ('DONE', 'CANCELLED')
            """)
    long countDueTodayByUserId(@Param("userId") UUID userId, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.dueDate < :today
              AND t.status NOT IN ('DONE', 'CANCELLED')
            """)
    long countOverdueByUserId(@Param("userId") UUID userId, @Param("today") LocalDate today);

    // Increments actual_minutes when a linked Pomodoro session ends
    @Modifying
    @Query("UPDATE Task t SET t.actualMinutes = t.actualMinutes + :delta WHERE t.id = :id")
    void incrementActualMinutes(@Param("id") UUID id, @Param("delta") int delta);

    // Plain-text LIKE fallback for dev environments without PostgreSQL FTS
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.position ASC, t.updatedAt DESC
            """)
    Page<Task> searchFallback(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);

    // Calendar range query — tasks with a due date within the given window
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.deleted = false
              AND t.dueDate BETWEEN :from AND :to
            ORDER BY t.dueDate ASC, t.dueTime ASC
            """)
    List<Task> findByUserIdAndDueDateBetween(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Batch check — all IDs belong to the given user (used by reorder validation)
    @Query("SELECT COUNT(t) FROM Task t WHERE t.id IN :ids AND t.userId = :userId")
    long countByIdsAndUserId(@Param("ids") List<UUID> ids, @Param("userId") UUID userId);

    // Nightly trash purge — ON DELETE CASCADE handles subtask removal in DB
    @Modifying
    @Query("DELETE FROM Task t WHERE t.deleted = true AND t.deletedAt < :cutoff")
    int purgeTrash(@Param("cutoff") Instant cutoff);
}
