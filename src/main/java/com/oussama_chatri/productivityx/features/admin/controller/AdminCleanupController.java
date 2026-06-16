package com.oussama_chatri.productivityx.features.admin.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.jobs.CleanupScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for manual operational triggers.
 *
 * <p>All routes require the ADMIN authority. In the current auth model this
 * is granted via a role claim in the JWT; non-admin users receive 403.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Operational endpoints — admin role required")
public class AdminCleanupController {

    private final CleanupScheduler cleanupScheduler;

    /**
     * Manually triggers the tombstone cleanup job that normally runs at 03:00 UTC.
     * Useful for testing the purge logic or reclaiming space ahead of schedule.
     */
    @PostMapping("/cleanup")
    @Operation(summary = "Trigger tombstone cleanup manually",
               description = "Runs the same purge logic as the scheduled job: " +
                             "permanently deletes notes, tasks, and events that have " +
                             "been in the trash for more than 30 days.")
    public ResponseEntity<ApiResponse<Void>> triggerCleanup() {
        cleanupScheduler.purgeDeletedRecords();
        return ResponseEntity.ok(ApiResponse.message("Tombstone cleanup triggered."));
    }
}
