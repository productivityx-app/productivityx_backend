package com.oussama_chatri.productivityx.features.search.service;

import com.oussama_chatri.productivityx.features.search.dto.response.SearchResponse;

public interface SearchService {

    SearchResponse search(String query, String types, int limit);
}
