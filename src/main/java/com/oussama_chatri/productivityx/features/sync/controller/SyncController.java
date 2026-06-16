package com.oussama_chatri.productivityx.features.sync.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.sync.dto.response.DeltaSyncResponse;
import com.oussama_chatri.productivityx.features.sync.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Sync", description = "Offline-first delta sync — pull all changes since a given timestamp with stable cursor pagination")
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/delta")
    @Operation(
            summary = "Pull changes since a given timestamp (cursor-paginated)",
            description = """
                    Returns entities modified after `since`, paginated by a stable cursor.
                    
                    **Pagination flow:**
                    1. Call with `since` only (no cursor) — returns first page.
                    2. If `hasMore == true`, call again with `cursor=<nextCursor>`.
                    3. Repeat until `hasMore == false`.
                    4. Store `syncedAt` from the last page as your new `since` value.
                    
                    **Why cursor instead of offset?**
                    Offset pagination breaks when new rows are inserted mid-sync.
                    Cursor uses `(updatedAt, id)` ordering which is stable regardless
                    of concurrent inserts.
                    
                    Soft-deleted entities are included — clients should remove them locally
                    when `deleted == true`.
                    """
    )
    public ResponseEntity<ApiResponse<DeltaSyncResponse>> delta(
            @Parameter(description = "ISO-8601 UTC timestamp. Return all entities changed after this point.")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant since,

            @Parameter(description = "Opaque cursor from the previous page's nextCursor field. Omit on the first call.")
            @RequestParam(required = false)
            String cursor,

            @Parameter(description = "Maximum items per page across all entity types combined. Range: 10–500.")
            @RequestParam(defaultValue = "100")
            int limit) {

        limit = Math.max(10, Math.min(500, limit));
        return ResponseEntity.ok(ApiResponse.ok(syncService.delta(since, cursor, limit)));
    }
}
