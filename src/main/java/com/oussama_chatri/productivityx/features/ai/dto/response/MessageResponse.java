package com.oussama_chatri.productivityx.features.ai.dto.response;

import com.oussama_chatri.productivityx.core.enums.MessageRole;
import com.oussama_chatri.productivityx.features.ai.entity.Message;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MessageResponse {

    private final UUID id;
    private final UUID conversationId;
    private final MessageRole role;
    private final String content;
    private final String actionBlock;
    private final Integer tokenCount;
    private final Instant createdAt;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .role(message.getRole())
                .content(message.getContent())
                .actionBlock(message.getActionBlock())
                .tokenCount(message.getTokenCount())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
