package com.oussama_chatri.productivityx.features.notes.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.notes.dto.request.AddTagToNoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.request.NoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;

import java.util.UUID;

public interface NoteService {

    NoteResponse create(NoteRequest request);

    NoteResponse getById(UUID noteId);

    PagedResponse<NoteResponse> listActive(int page, int size, UUID tagId, Boolean pinned);

    PagedResponse<NoteResponse> listTrash(int page, int size);

    NoteResponse update(UUID noteId, NoteRequest request);

    NoteResponse pin(UUID noteId);

    NoteResponse unpin(UUID noteId);

    NoteResponse softDelete(UUID noteId);

    NoteResponse restore(UUID noteId);

    void hardDelete(UUID noteId);

    NoteResponse addTag(UUID noteId, AddTagToNoteRequest request);

    NoteResponse removeTag(UUID noteId, UUID tagId);
}