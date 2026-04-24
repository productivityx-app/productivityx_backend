package com.oussama_chatri.productivityx.features.events.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class EventRequest {

    @NotBlank(message = "Event title is required.")
    @Size(max = 500, message = "Title must not exceed 500 characters.")
    private String title;

    private String description;

    @Size(max = 255, message = "Location must not exceed 255 characters.")
    private String location;

    @NotNull(message = "Start time is required.")
    private Instant startAt;

    @NotNull(message = "End time is required.")
    private Instant endAt;

    private Boolean allDay;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
             message = "Color must be a valid hex value (e.g. #6366F1).")
    private String color;

    // iCal RRULE — e.g. "FREQ=WEEKLY;BYDAY=MO,WE"
    @Size(max = 255, message = "Recurrence rule must not exceed 255 characters.")
    private String recurrenceRule;

    private Instant recurrenceEndAt;

    private Integer reminderMinutes;

    // When editing a recurring event: the parent ID of the series to modify
    private UUID recurrenceParentId;
}
