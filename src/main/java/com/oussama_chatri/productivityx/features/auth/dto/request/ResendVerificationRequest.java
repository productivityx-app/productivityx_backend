package com.oussama_chatri.productivityx.features.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email address format.")
    private String email;
}
