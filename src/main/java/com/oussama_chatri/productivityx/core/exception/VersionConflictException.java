package com.oussama_chatri.productivityx.core.exception;

import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.service.MergeService;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when a JPA optimistic lock conflict occurs on a note write.
 * Carries the current server-side entity so GlobalExceptionHandler can embed it
 * in the 409 response body — saving the client an extra GET request.
 */
@Getter
public class VersionConflictException extends RuntimeException {

    private final NoteResponse serverEntity;

    public VersionConflictException(NoteResponse serverEntity) {
        super("Version conflict: the note was modified by another session.");
        this.serverEntity = serverEntity;
    }
}
