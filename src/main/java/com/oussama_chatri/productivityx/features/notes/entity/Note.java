package com.oussama_chatri.productivityx.features.notes.entity;

import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notes", indexes = {
        @Index(name = "idx_notes_user_id",      columnList = "user_id"),
        @Index(name = "idx_notes_user_deleted",  columnList = "user_id, is_deleted"),
        @Index(name = "idx_notes_updated_at",    columnList = "user_id, updated_at DESC"),
        @Index(name = "idx_notes_sync_status",   columnList = "user_id, sync_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    @Builder.Default
    private String title = "";

    // Raw Markdown content
    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String content = "";

    // Stripped plain-text mirror for FTS and previews
    @Column(name = "plain_text_content", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String plainTextContent = "";

    @Column(name = "word_count", nullable = false)
    @Builder.Default
    private int wordCount = 0;

    @Column(name = "reading_time_seconds", nullable = false)
    @Builder.Default
    private int readingTimeSeconds = 0;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean pinned = false;

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

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "note_tags",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}