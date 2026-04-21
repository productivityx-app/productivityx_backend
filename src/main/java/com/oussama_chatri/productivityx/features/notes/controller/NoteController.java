package com.oussama_chatri.productivityx.features.notes.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.notes.dto.request.AddTagToNoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.request.NoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
@Tag(name = "Notes", description = "CRUD, pinning, trash/restore, and tag management for notes")
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    @Operation(summary = "Create a new note")
    public ResponseEntity<ApiResponse<NoteResponse>> create(
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(noteService.create(request), "Note created."));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single note by ID")
    public ResponseEntity<ApiResponse<NoteResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List active (non-deleted) notes — optionally filter by tagId or pinned")
    public ResponseEntity<ApiResponse<PagedResponse<NoteResponse>>> listActive(
            @RequestParam(defaultValue = "0")  int  page,
            @RequestParam(defaultValue = "20") int  size,
            @RequestParam(required = false)    UUID tagId,
            @RequestParam(required = false)    Boolean pinned) {
        return ResponseEntity.ok(ApiResponse.ok(
                noteService.listActive(page, size, tagId, pinned)));
    }

    @GetMapping("/trash")
    @Operation(summary = "List soft-deleted notes (trash)")
    public ResponseEntity<ApiResponse<PagedResponse<NoteResponse>>> listTrash(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.listTrash(page, size)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a note — all fields optional")
    public ResponseEntity<ApiResponse<NoteResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.update(id, request), "Note updated."));
    }

    @PatchMapping("/{id}/pin")
    @Operation(summary = "Pin a note")
    public ResponseEntity<ApiResponse<NoteResponse>> pin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.pin(id), "Note pinned."));
    }

    @PatchMapping("/{id}/unpin")
    @Operation(summary = "Unpin a note")
    public ResponseEntity<ApiResponse<NoteResponse>> unpin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.unpin(id), "Note unpinned."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a note (move to trash)")
    public ResponseEntity<ApiResponse<NoteResponse>> softDelete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.softDelete(id), "Note moved to trash."));
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "Restore a note from trash")
    public ResponseEntity<ApiResponse<NoteResponse>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.restore(id), "Note restored."));
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete a trashed note")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@PathVariable UUID id) {
        noteService.hardDelete(id);
        return ResponseEntity.ok(ApiResponse.message("Note permanently deleted."));
    }

    @PostMapping("/{id}/tags")
    @Operation(summary = "Add a tag to a note")
    public ResponseEntity<ApiResponse<NoteResponse>> addTag(
            @PathVariable UUID id,
            @Valid @RequestBody AddTagToNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.addTag(id, request), "Tag added."));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @Operation(summary = "Remove a tag from a note")
    public ResponseEntity<ApiResponse<NoteResponse>> removeTag(
            @PathVariable UUID id,
            @PathVariable UUID tagId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.removeTag(id, tagId), "Tag removed."));
    }
}