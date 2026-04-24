package com.oussama_chatri.productivityx.features.sync.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.sync.dto.response.DeltaSyncResponse;
import com.oussama_chatri.productivityx.features.sync.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Offline-first delta sync — pull all changes since a given timestamp")
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/delta")
    @Operation(
            summary = "Pull all changes since a given timestamp",
            description = "Returns all notes, tasks, events, and Pomodoro sessions modified " +
                          "after 'since' (ISO-8601 UTC). Call this after draining the outbox " +
                          "to catch any remote changes missed while offline."
    )
    public ResponseEntity<ApiResponse<DeltaSyncResponse>> delta(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.delta(since)));
    }
}
