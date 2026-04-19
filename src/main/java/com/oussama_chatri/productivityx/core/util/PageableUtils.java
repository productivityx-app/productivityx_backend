package com.oussama_chatri.productivityx.core.util;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PageableUtils {

    private static final int MAX_PAGE_SIZE     = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public Pageable build(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clamp(size));
    }

    public Pageable build(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clamp(size), sort);
    }

    public Pageable buildDefault() {
        return PageRequest.of(0, DEFAULT_PAGE_SIZE);
    }

    public <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }

    private int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
