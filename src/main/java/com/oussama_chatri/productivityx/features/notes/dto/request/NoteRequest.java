package com.oussama_chatri.productivityx.features.notes.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class NoteRequest {

    @Size(max = 500, message = "Title must not exceed 500 characters.")
    private String title;

    // Raw Markdown — no server-side size cap here; handled via DB column TEXT
    private String content;

    private Set<UUID> tagIds;

    private Boolean pinned;
}