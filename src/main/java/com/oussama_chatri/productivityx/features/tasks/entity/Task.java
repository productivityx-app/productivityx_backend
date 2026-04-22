package com.oussama_chatri.productivityx.features.tasks.entity;

import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_user_id",      columnList = "user_id"),
        @Index(name = "idx_tasks_parent",        columnList = "parent_task_id"),
        @Index(name = "idx_tasks_user_status",   columnList = "user_id, status"),
        @Index(name = "idx_tasks_user_due",      columnList = "user_id, due_date"),
        @Index(name = "idx_tasks_user_deleted",  columnList = "user_id, is_deleted"),
        @Index(name = "idx_tasks_updated_at",    columnList = "user_id, updated_at"),
        @Index(name = "idx_tasks_sync_status",   columnList = "user_id, sync_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Owning user — LAZY to avoid N+1 on list queries
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Denormalized for queries that only need the ID (avoids join)
    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    // Self-reference — nullable for top-level tasks
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    // Subtasks loaded explicitly on detail queries — never fetched on list queries
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> subtasks = new ArrayList<>();

    // Plain UUID — FK to events added in V11 migration; no JPA association here
    // to avoid circular dependency between features
    @Column(name = "linked_event_id")
    private UUID linkedEventId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "reminder_at")
    private Instant reminderAt;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    // Auto-incremented by PomodoroService when a linked session ends
    @Column(name = "actual_minutes", nullable = false)
    @Builder.Default
    private int actualMinutes = 0;

    // Set when status transitions to DONE; cleared when transitioning away from DONE
    @Column(name = "completed_at")
    private Instant completedAt;

    // Drag-and-drop position within the user's task list / kanban column
    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Incremented on every mutation — clients use this for conflict detection
    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    // Offline sync status — clients manage PENDING/SYNCING; server always writes SYNCED
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 10)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
