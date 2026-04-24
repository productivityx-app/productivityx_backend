package com.oussama_chatri.productivityx.features.ai.dto.response;

import com.oussama_chatri.productivityx.features.ai.entity.Conversation;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class ConversationResponse {

    private final UUID id;
    private final UUID userId;
    private final String title;
    private final boolean archived;
    private final List<MessageResponse> messages;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static ConversationResponse from(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .archived(conversation.isArchived())
                .messages(conversation.getMessages().stream()
                        .map(MessageResponse::from)
                        .collect(Collectors.toList()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    // Lightweight version — no messages (for list endpoints)
    public static ConversationResponse summary(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .archived(conversation.isArchived())
                .messages(List.of())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
