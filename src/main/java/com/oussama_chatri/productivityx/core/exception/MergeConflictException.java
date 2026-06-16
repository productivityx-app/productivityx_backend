package com.oussama_chatri.productivityx.core.exception;

import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.service.MergeService;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when a three-way text merge produces overlapping conflict regions.
 * Carries the server entity and the conflict list so GlobalExceptionHandler
 * can build a rich 409 response for the client's merge UI.
 */
@Getter
public class MergeConflictException extends RuntimeException {

    private final NoteResponse serverEntity;
    private final List<MergeService.ConflictRegion> conflicts;

    public MergeConflictException(NoteResponse serverEntity, List<MergeService.ConflictRegion> conflicts) {
        super("Merge conflict: overlapping edits detected.");
        this.serverEntity = serverEntity;
        this.conflicts = conflicts;
    }
}
