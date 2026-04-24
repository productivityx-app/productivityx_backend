package com.oussama_chatri.productivityx.features.search.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.search.dto.response.SearchResponse;
import com.oussama_chatri.productivityx.features.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Unified full-text search across notes, tasks, and events")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(
            summary = "Search across notes, tasks, and events",
            description = "Pass 'types' as a comma-separated list to restrict results " +
                          "(e.g. types=NOTE,TASK). Omit to search all types. " +
                          "Results are ordered by updatedAt desc."
    )
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @Parameter(description = "Search query — minimum 2 characters recommended")
            @RequestParam String q,

            @Parameter(description = "Comma-separated result types: NOTE, TASK, EVENT")
            @RequestParam(required = false) String types,

            @Parameter(description = "Maximum total results (default 10, max 50)")
            @RequestParam(defaultValue = "10") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(q, types, safeLimit)));
    }
}
