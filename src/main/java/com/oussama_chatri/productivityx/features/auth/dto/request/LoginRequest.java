package com.oussama_chatri.productivityx.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Identifier is required.")
    private String identifier;

    @NotBlank(message = "Password is required.")
    private String password;

    // Device info — sent by mobile/desktop clients to enable multi-device session tracking
    @Schema(description = "Stable device identifier (e.g. Firebase Installation ID)", example = "a1b2c3d4e5f6")
    private String deviceId;

    @Schema(description = "Human-readable device name", example = "iPhone 15 Pro")
    private String deviceName;

    @Schema(description = "Platform: ANDROID, IOS, DESKTOP, WEB", example = "ANDROID")
    private String platform;
}