package com.oussama_chatri.productivityx.features.notes.service;

import com.oussama_chatri.productivityx.features.notes.dto.request.TagRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.TagResponse;

import java.util.List;
import java.util.UUID;

public interface TagService {

    TagResponse create(TagRequest request);

    List<TagResponse> listAll();

    TagResponse update(UUID tagId, TagRequest request);

    void delete(UUID tagId);
}