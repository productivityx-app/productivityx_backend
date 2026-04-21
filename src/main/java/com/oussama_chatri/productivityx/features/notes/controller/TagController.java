package com.oussama_chatri.productivityx.features.notes.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.notes.dto.request.TagRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.TagResponse;
import com.oussama_chatri.productivityx.features.notes.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "User-scoped tag management")
public class TagController {

    private final TagService tagService;

    @PostMapping
    @Operation(summary = "Create a new tag")
    public ResponseEntity<ApiResponse<TagResponse>> create(
            @Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(tagService.create(request), "Tag created."));
    }

    @GetMapping
    @Operation(summary = "List all tags for the current user")
    public ResponseEntity<ApiResponse<List<TagResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(tagService.listAll()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tag's name or color")
    public ResponseEntity<ApiResponse<TagResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.update(id, request), "Tag updated."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tag — removes it from all notes automatically")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        tagService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Tag deleted."));
    }
}