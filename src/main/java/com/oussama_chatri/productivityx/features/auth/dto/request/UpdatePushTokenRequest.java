package com.oussama_chatri.productivityx.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePushTokenRequest {

    @NotBlank(message = "Push token is required.")
    @Schema(description = "FCM registration token for push notifications", example = "fK7yZ...")
    private String pushToken;
}