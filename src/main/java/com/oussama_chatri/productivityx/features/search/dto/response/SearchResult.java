package com.oussama_chatri.productivityx.features.search.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResult {

    public enum ResultType { NOTE, TASK, EVENT }

    private final UUID id;
    private final ResultType type;
    private final String title;
    private final String snippet;
    private final Instant updatedAt;
}
