package com.oussama_chatri.productivityx.features.notes.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.PageableUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.notes.dto.request.AddTagToNoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.request.NoteRequest;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.entity.Note;
import com.oussama_chatri.productivityx.features.notes.entity.Tag;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.notes.repository.TagRepository;
import com.oussama_chatri.productivityx.shared.websocket.WebSocketNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteServiceImpl implements NoteService {

    private final NoteRepository       noteRepository;
    private final TagRepository        tagRepository;
    private final SecurityUtils        securityUtils;
    private final PageableUtils        pageableUtils;
    private final NoteContentProcessor contentProcessor;
    private final WebSocketNotifier    wsNotifier;

    @Override
    @Transactional
    public NoteResponse create(NoteRequest request) {
        User user  = securityUtils.currentUser();
        Note note  = buildNote(user, request);
        Note saved = noteRepository.save(note);

        wsNotifier.notifyUser(user.getId(), "notes.created", NoteResponse.from(saved));
        log.debug("Note created id={} user={}", saved.getId(), user.getId());
        return NoteResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getById(UUID noteId) {
        UUID userId = securityUtils.currentUserId();
        return NoteResponse.from(findOwnedNote(noteId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteResponse> listActive(int page, int size, UUID tagId, Boolean pinned) {
        UUID userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size,
                Sort.by(Sort.Direction.DESC, "pinned", "updatedAt"));

        Page<Note> notePage;
        if (tagId != null) {
            notePage = noteRepository.findActiveByUserIdAndTagId(userId, tagId, pageable);
        } else if (Boolean.TRUE.equals(pinned)) {
            notePage = noteRepository.findPinnedByUserId(userId, pageable);
        } else {
            notePage = noteRepository.findActiveByUserId(userId, pageable);
        }

        return pageableUtils.toPagedResponse(notePage.map(NoteResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteResponse> listTrash(int page, int size) {
        UUID userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);
        return pageableUtils.toPagedResponse(
                noteRepository.findDeletedByUserId(userId, pageable).map(NoteResponse::from));
    }

    @Override
    @Transactional
    public NoteResponse update(UUID noteId, NoteRequest request) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);

        if (note.isDeleted()) {
            throw AppException.badRequest(ErrorCode.VAL_NOTE_TRASHED);
        }

        applyContentUpdate(note, request);
        note.setVersion(note.getVersion() + 1);

        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Override
    @Transactional
    public NoteResponse pin(UUID noteId) {
        return setPinned(noteId, true);
    }

    @Override
    @Transactional
    public NoteResponse unpin(UUID noteId) {
        return setPinned(noteId, false);
    }

    @Override
    @Transactional
    public NoteResponse softDelete(UUID noteId) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);

        note.setDeleted(true);
        note.setDeletedAt(Instant.now());
        note.setPinned(false);

        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.deleted", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Override
    @Transactional
    public NoteResponse restore(UUID noteId) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);

        if (!note.isDeleted()) {
            throw AppException.badRequest(ErrorCode.VAL_NOTE_NOT_IN_TRASH);
        }

        note.setDeleted(false);
        note.setDeletedAt(null);

        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.restored", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Override
    @Transactional
    public void hardDelete(UUID noteId) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);

        if (!note.isDeleted()) {
            throw AppException.badRequest(ErrorCode.VAL_NOTE_MUST_BE_TRASHED_FIRST);
        }

        noteRepository.delete(note);
        wsNotifier.notifyUser(userId, "notes.deleted", noteId);
    }

    @Override
    @Transactional
    public NoteResponse addTag(UUID noteId, AddTagToNoteRequest request) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);
        Tag  tag    = tagRepository.findByIdAndUserId(request.getTagId(), userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TAG_NOT_FOUND));

        note.getTags().add(tag);
        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Override
    @Transactional
    public NoteResponse removeTag(UUID noteId, UUID tagId) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);
        Tag  tag    = tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TAG_NOT_FOUND));

        note.getTags().remove(tag);
        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    private Note buildNote(User user, NoteRequest request) {
        String content   = request.getContent() != null ? request.getContent() : "";
        String plainText = contentProcessor.toPlainText(content);
        int    wordCount = contentProcessor.wordCount(plainText);

        Note.NoteBuilder builder = Note.builder()
                .user(user)
                .title(request.getTitle() != null ? request.getTitle().trim() : "")
                .content(content)
                .plainTextContent(plainText)
                .wordCount(wordCount)
                .readingTimeSeconds(contentProcessor.readingTimeSeconds(wordCount))
                .pinned(Boolean.TRUE.equals(request.getPinned()));

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            builder.tags(resolveTagsForUser(request.getTagIds(), user.getId()));
        }

        return builder.build();
    }

    private void applyContentUpdate(Note note, NoteRequest request) {
        if (request.getTitle() != null) {
            note.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            String plainText = contentProcessor.toPlainText(request.getContent());
            int    wordCount = contentProcessor.wordCount(plainText);
            note.setContent(request.getContent());
            note.setPlainTextContent(plainText);
            note.setWordCount(wordCount);
            note.setReadingTimeSeconds(contentProcessor.readingTimeSeconds(wordCount));
        }
        if (request.getPinned() != null) {
            note.setPinned(request.getPinned());
        }
        if (request.getTagIds() != null) {
            note.setTags(resolveTagsForUser(request.getTagIds(), note.getUserId()));
        }
    }

    private NoteResponse setPinned(UUID noteId, boolean pinned) {
        UUID userId = securityUtils.currentUserId();
        Note note   = findOwnedNote(noteId, userId);
        note.setPinned(pinned);
        Note saved = noteRepository.save(note);
        wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    private Note findOwnedNote(UUID noteId, UUID userId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_NOTE_NOT_FOUND));
    }

    // Single-query bulk tag load — eliminates N+1 from iterating individually
    private Set<Tag> resolveTagsForUser(Set<UUID> tagIds, UUID userId) {
        Set<Tag> tags = tagRepository.findAllByIdInAndUserId(tagIds, userId);
        if (tags.size() != tagIds.size()) {
            throw AppException.notFound(ErrorCode.RES_TAG_NOT_FOUND);
        }
        return tags;
    }
}
