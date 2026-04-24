package com.oussama_chatri.productivityx.features.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Message content is required.")
    @Size(max = 10000, message = "Message must not exceed 10,000 characters.")
    private String content;
}
