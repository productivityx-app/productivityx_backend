package com.oussama_chatri.productivityx.features.notes.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddTagToNoteRequest {

    @NotNull(message = "Tag ID is required.")
    private UUID tagId;
}