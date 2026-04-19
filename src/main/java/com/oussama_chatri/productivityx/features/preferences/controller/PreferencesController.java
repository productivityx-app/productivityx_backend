package com.oussama_chatri.productivityx.features.preferences.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.preferences.dto.request.UpdatePreferencesRequest;
import com.oussama_chatri.productivityx.features.preferences.dto.response.UserPreferencesResponse;
import com.oussama_chatri.productivityx.features.preferences.service.PreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
@Tag(name = "Preferences", description = "User preferences — Pomodoro settings, notifications, views, AI, display")
public class PreferencesController {

    private final PreferencesService preferencesService;

    @GetMapping
    @Operation(summary = "Get the current user's preferences")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.ok(preferencesService.getPreferences()));
    }

    @PutMapping
    @Operation(summary = "Update preferences — all fields are optional (partial updates supported)")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                preferencesService.updatePreferences(request), "Preferences updated."));
    }
}
