package com.oussama_chatri.productivityx.features.pomodoro.repository;

import com.oussama_chatri.productivityx.core.enums.PomodoroType;
import com.oussama_chatri.productivityx.features.pomodoro.entity.PomodoroSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    @Query("SELECT s FROM PomodoroSession s WHERE s.id = :id AND s.userId = :userId")
    Optional<PomodoroSession> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.endedAt IS NULL
            ORDER BY s.startedAt DESC
            LIMIT 1
            """)
    Optional<PomodoroSession> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId
            ORDER BY s.startedAt DESC
            """)
    Page<PomodoroSession> findByUserIdOrderByStartedAtDesc(
            @Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.taskId = :taskId
            ORDER BY s.startedAt DESC
            """)
    Page<PomodoroSession> findByUserIdAndTaskId(
            @Param("userId") UUID userId,
            @Param("taskId") UUID taskId,
            Pageable pageable);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.type = 'FOCUS'
              AND s.completed = true
              AND s.startedAt >= :dayStart
              AND s.startedAt < :dayEnd
            ORDER BY s.startedAt DESC
            """)
    List<PomodoroSession> findCompletedFocusTodayByUserId(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    @Query("""
            SELECT COALESCE(SUM(s.actualDurationSeconds), 0)
            FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.type = 'FOCUS'
              AND s.completed = true
              AND s.startedAt >= :dayStart
              AND s.startedAt < :dayEnd
            """)
    long sumActualFocusSecondsToday(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.type = :type
              AND s.startedAt >= :from
              AND s.startedAt < :to
            ORDER BY s.startedAt DESC
            """)
    List<PomodoroSession> findByUserIdAndTypeAndRange(
            @Param("userId") UUID userId,
            @Param("type") PomodoroType type,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COUNT(s) FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.type = 'FOCUS'
              AND s.completed = true
              AND s.startedAt >= :dayStart
              AND s.startedAt < :dayEnd
            """)
    long countCompletedFocusTodayByUserId(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    @Query("""
            SELECT s FROM PomodoroSession s
            WHERE s.userId = :userId AND s.createdAt > :since
            ORDER BY s.createdAt ASC
            """)
    List<PomodoroSession> findCreatedSince(
            @Param("userId") UUID userId,
            @Param("since") Instant since);

    // ── NEW: used by AiContextDataProviderImpl ────────────────────────────

    /**
     * Returns the title of the task linked to the user's currently active FOCUS session.
     * Null when no session is running or the session has no linked task.
     */
    @Query("""
            SELECT t.title FROM PomodoroSession s
            JOIN Task t ON t.id = s.taskId
            WHERE s.userId = :userId
              AND s.type = 'FOCUS'
              AND s.endedAt IS NULL
            LIMIT 1
            """)
    Optional<String> findCurrentFocusTaskTitle(@Param("userId") UUID userId);

    /**
     * Total actual focus seconds for all completed FOCUS sessions started on or after dayStart.
     * Used to compute today's focus minutes for the AI context panel.
     */
    @Query("""
            SELECT COALESCE(SUM(s.actualDurationSeconds), 0)
            FROM PomodoroSession s
            WHERE s.userId = :userId
              AND s.type = 'FOCUS'
              AND s.completed = true
              AND s.startedAt >= :dayStart
            """)
    int sumFocusMinutesToday(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart);
}
