package com.oussama_chatri.productivityx.features.events.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.events.dto.request.EventRequest;
import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.events.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Calendar event management — CRUD, recurrence, trash/restore")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "Create a calendar event",
               description = "Pass recurrenceRule (iCal RRULE) for recurring events. " +
                             "Pass recurrenceParentId to create an instance of an existing series.")
    public ResponseEntity<ApiResponse<EventResponse>> create(
            @Valid @RequestBody EventRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(eventService.create(request), "Event created."));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single event by ID")
    public ResponseEntity<ApiResponse<EventResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List events",
               description = "Pass 'from' and 'to' (ISO-8601 UTC or date-only) for a calendar range query. " +
                             "Omit both for a paged listing ordered by startAt.")
    public ResponseEntity<ApiResponse<?>> list(
            @Parameter(description = "Range start — ISO-8601 UTC (e.g. 2026-04-01T00:00:00Z) or date-only (e.g. 2026-04-01)")
            @RequestParam(required = false) String from,

            @Parameter(description = "Range end — ISO-8601 UTC (e.g. 2026-04-30T23:59:59Z) or date-only (e.g. 2026-04-30)")
            @RequestParam(required = false) String to,

            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        if (from != null && to != null) {
            Instant fromInstant = parseInstant(from);
            Instant toInstant = parseInstant(to);
            List<EventResponse> events = eventService.listByRange(fromInstant, toInstant);
            return ResponseEntity.ok(ApiResponse.ok(events));
        }

        PagedResponse<EventResponse> paged = eventService.listPaged(page, size);
        return ResponseEntity.ok(ApiResponse.ok(paged));
    }

    @GetMapping("/trash")
    @Operation(summary = "List trashed events")
    public ResponseEntity<ApiResponse<PagedResponse<EventResponse>>> listTrash(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listTrash(page, size)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an event — all fields are optional",
               description = "Only non-null fields in the request body are applied.")
    public ResponseEntity<ApiResponse<EventResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(eventService.update(id, request), "Event updated."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete an event (move to trash)")
    public ResponseEntity<ApiResponse<EventResponse>> softDelete(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(eventService.softDelete(id), "Event moved to trash."));
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "Restore an event from trash")
    public ResponseEntity<ApiResponse<EventResponse>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(eventService.restore(id), "Event restored."));
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete an event — must be in trash first")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@PathVariable UUID id) {
        eventService.hardDelete(id);
        return ResponseEntity.ok(ApiResponse.message("Event permanently deleted."));
    }

    @DeleteMapping("/{id}/series")
    @Operation(summary = "Delete all instances of a recurring series",
               description = "Pass the parent event ID. " +
                             "Soft-deletes the parent and all its recurring instances.")
    public ResponseEntity<ApiResponse<Void>> deleteSeries(@PathVariable UUID id) {
        eventService.deleteSeriesFromParent(id);
        return ResponseEntity.ok(ApiResponse.message("Recurring series deleted."));
    }

    /**
     * Parses an Instant from a string that may be either:
     * - Full ISO-8601 datetime: 2026-04-01T00:00:00Z
     * - Date-only: 2026-04-01 (assumes start of day UTC)
     */
    private Instant parseInstant(String value) {
        if (value.contains("T")) {
            return Instant.parse(value);
        }
        return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
