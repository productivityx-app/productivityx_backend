package com.oussama_chatri.productivityx.features.pomodoro.entity;

import com.oussama_chatri.productivityx.core.enums.PomodoroType;
import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pomodoro_sessions", indexes = {
        @Index(name = "idx_pomodoro_user_id",   columnList = "user_id"),
        @Index(name = "idx_pomodoro_task",       columnList = "task_id"),
        @Index(name = "idx_pomodoro_user_date",  columnList = "user_id, started_at DESC"),
        @Index(name = "idx_pomodoro_user_type",  columnList = "user_id, type"),
        @Index(name = "idx_pomodoro_completed",  columnList = "user_id, completed, started_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PomodoroSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    // Plain UUID — avoids circular JPA dependency between pomodoro and tasks features
    @Column(name = "task_id")
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PomodoroType type;

    // Planned duration copied from UserPreferences at session start — history stays accurate
    // even after the user later changes their Pomodoro settings
    @Column(name = "planned_duration_seconds", nullable = false)
    private int plannedDurationSeconds;

    // Null until the session ends (completed or interrupted)
    @Column(name = "actual_duration_seconds")
    private Integer actualDurationSeconds;

    @Column(nullable = false)
    @Builder.Default
    private boolean interrupted = false;

    @Column(name = "interrupt_reason", length = 255)
    private String interruptReason;

    // Settings snapshot — same rationale as plannedDurationSeconds
    @Column(name = "focus_minutes_setting", nullable = false)
    private int focusMinutesSetting;

    @Column(name = "short_break_minutes_setting", nullable = false)
    private int shortBreakMinutesSetting;

    @Column(name = "long_break_minutes_setting", nullable = false)
    private int longBreakMinutesSetting;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // Null while session is active
    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
