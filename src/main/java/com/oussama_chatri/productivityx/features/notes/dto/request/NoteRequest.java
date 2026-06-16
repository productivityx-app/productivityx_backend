package com.oussama_chatri.productivityx.features.notes.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class NoteRequest {

    @Size(max = 500, message = "Title must not exceed 500 characters.")
    private String title;

    private String content;

    private Set<UUID> tagIds;

    private Boolean pinned;

    /**
     * The business version the client last observed for this note.
     * Required on PUT to enable conflict detection and three-way merge.
     * Ignored on POST (creation).
     * Workflow:
     *   - Client reads GET /notes/{id} → receives {version: 3, updatedAt: "..."}
     *   - Client sends PUT /notes/{id} with {knownVersion: 3, clientUpdatedAt: "..."}
     *   - Server compares knownVersion with stored version before writing
     */
    private Integer knownVersion;

    /**
     * The updatedAt timestamp the client last saw, in ISO-8601 UTC.
     * Used to detect server-side changes that happened between the client's
     * last fetch and this write attempt.
     * The server NEVER uses this as the entity's updatedAt — the DB trigger
     * always sets updated_at = NOW(). This field is used only for conflict
     * detection comparison and is logged for audit purposes.
     */
    private String clientUpdatedAt;
}
