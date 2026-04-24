package com.oussama_chatri.productivityx.features.search.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SearchResponse {

    private final List<SearchResult> results;
    private final int total;
    private final String query;
}
