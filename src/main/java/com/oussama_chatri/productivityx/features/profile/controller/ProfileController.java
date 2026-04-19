package com.oussama_chatri.productivityx.features.profile.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateAvatarRequest;
import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateProfileRequest;
import com.oussama_chatri.productivityx.features.profile.dto.response.ProfileResponse;
import com.oussama_chatri.productivityx.features.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile — name, avatar, bio, timezone, language, theme")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get the current user's profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile()));
    }

    @PutMapping
    @Operation(summary = "Update the current user's profile — all fields are optional")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateProfile(request), "Profile updated."));
    }

    @PatchMapping("/avatar")
    @Operation(summary = "Update the current user's avatar URL")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(
            @Valid @RequestBody UpdateAvatarRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateAvatar(request), "Avatar updated."));
    }
}
