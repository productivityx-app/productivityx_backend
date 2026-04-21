package com.oussama_chatri.productivityx.features.notes.dto.response;

import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.features.notes.entity.Note;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class NoteResponse {

    private final UUID id;
    private final UUID userId;
    private final String title;
    private final String content;
    private final String plainTextContent;
    private final int wordCount;
    private final int readingTimeSeconds;
    private final boolean pinned;
    private final boolean deleted;
    private final Instant deletedAt;
    private final int version;
    private final SyncStatus syncStatus;
    private final Set<TagResponse> tags;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static NoteResponse from(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .userId(note.getUserId())
                .title(note.getTitle())
                .content(note.getContent())
                .plainTextContent(note.getPlainTextContent())
                .wordCount(note.getWordCount())
                .readingTimeSeconds(note.getReadingTimeSeconds())
                .pinned(note.isPinned())
                .deleted(note.isDeleted())
                .deletedAt(note.getDeletedAt())
                .version(note.getVersion())
                .syncStatus(note.getSyncStatus())
                .tags(note.getTags().stream()
                        .map(TagResponse::from)
                        .collect(Collectors.toSet()))
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}