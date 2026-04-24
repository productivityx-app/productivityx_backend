package com.oussama_chatri.productivityx.features.events.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.features.events.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {

    private final UUID id;
    private final UUID userId;

    // Null for non-recurring or parent events
    private final UUID recurrenceParentId;

    private final String title;
    private final String description;
    private final String location;

    private final Instant startAt;
    private final Instant endAt;

    private final boolean allDay;
    private final String color;

    private final String recurrenceRule;
    private final Instant recurrenceEndAt;

    private final Integer reminderMinutes;

    private final boolean deleted;
    private final Instant deletedAt;

    private final int version;
    private final SyncStatus syncStatus;

    private final Instant createdAt;
    private final Instant updatedAt;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .recurrenceParentId(
                        event.getRecurrenceParent() != null
                                ? event.getRecurrenceParent().getId()
                                : null)
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .allDay(event.isAllDay())
                .color(event.getColor())
                .recurrenceRule(event.getRecurrenceRule())
                .recurrenceEndAt(event.getRecurrenceEndAt())
                .reminderMinutes(event.getReminderMinutes())
                .deleted(event.isDeleted())
                .deletedAt(event.getDeletedAt())
                .version(event.getVersion())
                .syncStatus(event.getSyncStatus())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
