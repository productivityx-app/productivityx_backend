package com.oussama_chatri.productivityx.features.notes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagRequest {

    @NotBlank(message = "Tag name is required.")
    @Size(min = 1, max = 50, message = "Tag name must be between 1 and 50 characters.")
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color (e.g. #6366F1).")
    private String color;
}