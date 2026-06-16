package com.oussama_chatri.productivityx.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationRequest {

    @Schema(example = "user@example.com", format = "email")
    @Email(message = "Invalid email address format.")
    @NotBlank(message = "Email is required.")
    private String email;
}
