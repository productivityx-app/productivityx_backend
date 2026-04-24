package com.oussama_chatri.productivityx.features.pomodoro.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.EndSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.InterruptSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.StartSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroStatsResponse;
import com.oussama_chatri.productivityx.features.pomodoro.service.PomodoroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pomodoro")
@RequiredArgsConstructor
@Tag(name = "Pomodoro", description = "Pomodoro session lifecycle — start, end, interrupt, history, stats")
public class PomodoroController {

    private final PomodoroService pomodoroService;

    @PostMapping("/sessions/start")
    @Operation(summary = "Start a new Pomodoro session",
               description = "Rejects with 400 if an active session already exists. " +
                             "Session settings are snapshotted from the user's current preferences " +
                             "so history stays accurate after settings change. " +
                             "Pass taskId to link the session to a task — focus time is credited automatically on end.")
    public ResponseEntity<ApiResponse<PomodoroSessionResponse>> startSession(
            @Valid @RequestBody StartSessionRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(pomodoroService.startSession(request), "Session started."));
    }

    @PatchMapping("/sessions/{id}/end")
    @Operation(summary = "End (complete) an active session",
               description = "Marks the session as completed and credits actual focus minutes " +
                             "to the linked task. The client may supply actualDurationSeconds " +
                             "for accuracy — the server validates it against its own clock.")
    public ResponseEntity<ApiResponse<PomodoroSessionResponse>> endSession(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) EndSessionRequest request) {
        EndSessionRequest body = request != null ? request : new EndSessionRequest();
        return ResponseEntity.ok(
                ApiResponse.ok(pomodoroService.endSession(id, body), "Session completed."));
    }

    @PatchMapping("/sessions/{id}/interrupt")
    @Operation(summary = "Interrupt (stop early) an active session",
               description = "Marks the session as interrupted. Partial focus time is still credited " +
                             "to the linked task. An optional reason can be supplied.")
    public ResponseEntity<ApiResponse<PomodoroSessionResponse>> interruptSession(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) InterruptSessionRequest request) {
        InterruptSessionRequest body = request != null ? request : new InterruptSessionRequest();
        return ResponseEntity.ok(
                ApiResponse.ok(pomodoroService.interruptSession(id, body), "Session interrupted."));
    }

    @GetMapping("/sessions/active")
    @Operation(summary = "Get the currently active session",
               description = "Returns null data (not an error) when no session is running. " +
                             "Clients poll this on app foreground to resume a session started " +
                             "on another device.")
    public ResponseEntity<ApiResponse<PomodoroSessionResponse>> getActiveSession() {
        return ResponseEntity.ok(ApiResponse.ok(pomodoroService.getActiveSession()));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get a single session by ID")
    public ResponseEntity<ApiResponse<PomodoroSessionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(pomodoroService.getById(id)));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List session history",
               description = "Returns all sessions newest-first. Pass taskId to filter " +
                             "to sessions linked to a specific task.")
    public ResponseEntity<ApiResponse<PagedResponse<PomodoroSessionResponse>>> listSessions(
            @RequestParam(defaultValue = "0")  int  page,
            @RequestParam(defaultValue = "20") int  size,
            @Parameter(description = "Filter to sessions linked to this task ID")
            @RequestParam(required = false)    UUID taskId) {
        return ResponseEntity.ok(
                ApiResponse.ok(pomodoroService.listSessions(page, size, taskId)));
    }

    @GetMapping("/stats/today")
    @Operation(summary = "Get today's Pomodoro stats",
               description = "Returns completed session count and total focus minutes for today (UTC). " +
                             "Used by the home dashboard and the AI context panel.")
    public ResponseEntity<ApiResponse<PomodoroStatsResponse>> getTodayStats() {
        return ResponseEntity.ok(ApiResponse.ok(pomodoroService.getTodayStats()));
    }
}
