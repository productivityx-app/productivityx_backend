package com.oussama_chatri.productivityx.features.search.service;

import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.search.dto.response.SearchResponse;
import com.oussama_chatri.productivityx.features.search.dto.response.SearchResult;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_SNIPPET_LENGTH = 150;

    private final NoteRepository  noteRepository;
    private final TaskRepository  taskRepository;
    private final EventRepository eventRepository;
    private final SecurityUtils   securityUtils;

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String types, int limit) {
        if (query == null || query.isBlank()) {
            return SearchResponse.builder().results(List.of()).total(0).query("").build();
        }

        String      q          = query.trim();
        UUID        userId     = securityUtils.currentUserId();
        Set<String> typeFilter = parseTypes(types);

        int perType = Math.max(1, limit / Math.max(1, typeFilter.size()));
        PageRequest pr = PageRequest.of(0, perType);

        List<SearchResult> results = new ArrayList<>();

        if (typeFilter.contains("NOTE")) {
            noteRepository.searchFallback(userId, q, pr).forEach(note ->
                    results.add(SearchResult.builder()
                            .id(note.getId())
                            .type(SearchResult.ResultType.NOTE)
                            .title(note.getTitle().isBlank() ? "Untitled" : note.getTitle())
                            .snippet(truncate(note.getPlainTextContent()))
                            .updatedAt(note.getUpdatedAt())
                            .build()));
        }

        if (typeFilter.contains("TASK")) {
            taskRepository.searchFallback(userId, q, pr).forEach(task ->
                    results.add(SearchResult.builder()
                            .id(task.getId())
                            .type(SearchResult.ResultType.TASK)
                            .title(task.getTitle())
                            .snippet(truncate(task.getDescription()))
                            .updatedAt(task.getUpdatedAt())
                            .build()));
        }

        if (typeFilter.contains("EVENT")) {
            eventRepository.searchFallback(userId, q, pr).forEach(event ->
                    results.add(SearchResult.builder()
                            .id(event.getId())
                            .type(SearchResult.ResultType.EVENT)
                            .title(event.getTitle())
                            .snippet(truncate(event.getDescription()))
                            .updatedAt(event.getUpdatedAt())
                            .build()));
        }

        results.sort(Comparator.comparing(
                r -> r.getUpdatedAt() != null ? r.getUpdatedAt() : Instant.MIN,
                Comparator.reverseOrder()));

        List<SearchResult> limited = results.stream().limit(limit).collect(Collectors.toList());
        return SearchResponse.builder()
                .results(limited)
                .total(limited.size())
                .query(q)
                .build();
    }

    private Set<String> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return Set.of("NOTE", "TASK", "EVENT");
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private String truncate(String text) {
        if (text == null || text.isBlank()) return null;
        return text.length() <= MAX_SNIPPET_LENGTH
                ? text
                : text.substring(0, MAX_SNIPPET_LENGTH - 1).trim() + "…";
    }
}
