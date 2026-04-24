package com.oussama_chatri.productivityx.features.events.entity;

import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_user_id",     columnList = "user_id"),
        @Index(name = "idx_events_user_range",  columnList = "user_id, start_at, end_at"),
        @Index(name = "idx_events_user_deleted",columnList = "user_id, is_deleted"),
        @Index(name = "idx_events_recurrence",  columnList = "recurrence_parent_id"),
        @Index(name = "idx_events_updated_at",  columnList = "user_id, updated_at"),
        @Index(name = "idx_events_sync_status", columnList = "user_id, sync_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    // Self-reference for recurring event instances — null on the parent/template
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_parent_id")
    private Event recurrenceParent;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "is_all_day", nullable = false)
    @Builder.Default
    private boolean allDay = false;

    // Hex color — e.g. "#6366F1"
    @Column(nullable = false, length = 7)
    @Builder.Default
    private String color = "#6366F1";

    // iCal RRULE string — e.g. "FREQ=WEEKLY;BYDAY=MO,WE,FR"
    @Column(name = "recurrence_rule", length = 255)
    private String recurrenceRule;

    @Column(name = "recurrence_end_at")
    private Instant recurrenceEndAt;

    // Minutes before the event to fire a reminder notification
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

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
