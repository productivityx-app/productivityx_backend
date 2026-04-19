package com.oussama_chatri.productivityx.features.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank(message = "Password is required to confirm account deletion.")
    private String password;
}