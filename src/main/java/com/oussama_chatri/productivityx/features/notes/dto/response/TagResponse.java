package com.oussama_chatri.productivityx.features.notes.dto.response;

import com.oussama_chatri.productivityx.features.notes.entity.Tag;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TagResponse {

    private final UUID id;
    private final UUID userId;
    private final String name;
    private final String color;
    private final Instant createdAt;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .userId(tag.getUserId())
                .name(tag.getName())
                .color(tag.getColor())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}