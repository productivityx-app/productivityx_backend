package com.oussama_chatri.productivityx.features.notes.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.notes.dto.request.TagRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.TagResponse;
import com.oussama_chatri.productivityx.features.notes.entity.Tag;
import com.oussama_chatri.productivityx.features.notes.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public TagResponse create(TagRequest request) {
        User user = securityUtils.currentUser();

        if (tagRepository.existsByUserIdAndName(user.getId(), request.getName().trim())) {
            throw AppException.conflict(ErrorCode.VAL_CONSTRAINT_VIOLATION);
        }

        Tag tag = tagRepository.save(Tag.builder()
                .user(user)
                .name(request.getName().trim())
                .color(request.getColor() != null ? request.getColor() : "#6366F1")
                .build());

        return TagResponse.from(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> listAll() {
        UUID userId = securityUtils.currentUserId();
        return tagRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(TagResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TagResponse update(UUID tagId, TagRequest request) {
        UUID userId = securityUtils.currentUserId();
        Tag  tag    = findOwnedTag(tagId, userId);

        if (!tag.getName().equals(request.getName().trim())
                && tagRepository.existsByUserIdAndName(userId, request.getName().trim())) {
            throw AppException.conflict(ErrorCode.VAL_CONSTRAINT_VIOLATION);
        }

        tag.setName(request.getName().trim());
        if (request.getColor() != null) tag.setColor(request.getColor());

        return TagResponse.from(tagRepository.save(tag));
    }

    @Override
    @Transactional
    public void delete(UUID tagId) {
        UUID userId = securityUtils.currentUserId();
        Tag  tag    = findOwnedTag(tagId, userId);
        // JPA cascade removes rows from note_tags join table automatically
        tagRepository.delete(tag);
    }

    private Tag findOwnedTag(UUID tagId, UUID userId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TAG_NOT_FOUND));
    }
}