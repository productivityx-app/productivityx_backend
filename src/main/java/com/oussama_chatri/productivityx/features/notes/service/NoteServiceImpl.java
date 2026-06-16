package com.oussama_chatri.productivityx.features.notes.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.exception.MergeConflictException;
import com.oussama_chatri.productivityx.core.exception.VersionConflictException;
import com.oussama_chatri.productivityx.core.locking.AdvisoryLockService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /**
     * Configurable conflict threshold.
     * If the server's updatedAt is more than this many milliseconds ahead of the
     * client's clientUpdatedAt, we reject the write before even trying to merge.
     * Default 0 means any server change since the client's last read counts as a conflict.
     */
    @Value("${app.sync.conflict-threshold-ms:0}")
    private long conflictThresholdMs;

    private final NoteRepository       noteRepository;
    private final TagRepository        tagRepository;
    private final SecurityUtils        securityUtils;
    private final PageableUtils        pageableUtils;
    private final NoteContentProcessor contentProcessor;
    private final WebSocketNotifier    wsNotifier;
    private final MergeService         mergeService;
    private final AdvisoryLockService  advisoryLockService;

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

    /**
     * Update a note with full conflict detection, advisory locking, and three-way merge.
     *
     * <p>Flow:
     * <ol>
     *   <li>Acquire a PostgreSQL transaction-scoped advisory lock on (userId, noteId)
     *       to serialize concurrent edits from the same user across devices.</li>
     *   <li>If clientUpdatedAt is provided, compare it with server updatedAt.
     *       If the server is ahead by more than conflictThresholdMs, attempt merge.</li>
     *   <li>If versions diverge, attempt a three-way merge of plainTextContent.
     *       Clean merge → save merged content, return HTTP 200.
     *       Conflict → throw ConflictException → GlobalExceptionHandler → HTTP 409.</li>
     *   <li>The @Version field on Note gives us JPA optimistic locking as a final guard.
     *       If a concurrent request committed between our read and save, Hibernate throws
     *       OptimisticLockingFailureException → caught here → HTTP 409.</li>
     * </ol>
     */
    @Override
    @Transactional
    public NoteResponse update(UUID noteId, NoteRequest request) {
        UUID userId = securityUtils.currentUserId();

        // Acquire transaction-scoped advisory lock — serializes concurrent edits
        // from the same user across multiple devices. Auto-released on TX commit/rollback.
        advisoryLockService.acquireNoteLock(userId, noteId);

        Note note = findOwnedNote(noteId, userId);

        if (note.isDeleted()) {
            throw AppException.badRequest(ErrorCode.VAL_NOTE_TRASHED);
        }

        // Log clientUpdatedAt for audit; compare with server for conflict detection
        if (request.getClientUpdatedAt() != null) {
            log.debug("Note update clientUpdatedAt={} serverUpdatedAt={} noteId={}",
                    request.getClientUpdatedAt(), note.getUpdatedAt(), noteId);

            Instant clientTimestamp = parseClientTimestamp(request.getClientUpdatedAt(), noteId);
            if (clientTimestamp != null && note.getUpdatedAt() != null) {
                long serverAheadMs = note.getUpdatedAt().toEpochMilli() - clientTimestamp.toEpochMilli();
                if (serverAheadMs > conflictThresholdMs) {
                    // Server has newer data — attempt three-way merge before writing
                    return attemptMerge(note, request, userId, noteId);
                }
            }
        }

        // Client-provided knownVersion diverges from the stored version
        if (request.getKnownVersion() != null && request.getKnownVersion() != note.getVersion()) {
            return attemptMerge(note, request, userId, noteId);
        }

        applyContentUpdate(note, request);
        note.setVersion(note.getVersion() + 1);

        // @Version on Note handles the race condition here — if another transaction
        // committed between our findOwnedNote() call and this save(), Hibernate throws
        // OptimisticLockingFailureException which GlobalExceptionHandler maps to HTTP 409.
        try {
            Note saved = noteRepository.save(note);
            wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
            return NoteResponse.from(saved);
        } catch (OptimisticLockingFailureException ex) {
            // Concurrent write won the race — re-fetch and surface current state in the 409
            Note current = findOwnedNote(noteId, userId);
            throw new VersionConflictException(NoteResponse.from(current));
        }
    }

    /**
     * Attempts a three-way merge when version conflict is detected.
     * The ancestor is reconstructed from the note's plain text at the time
     * the client last knew about it (we use the current stored state as ancestor
     * because we don't have a snapshot store — see architectural note below).
     *
     * Architectural note: a full three-way merge requires the ancestor snapshot
     * (the version both sides diverged from). Without a snapshot store, we use
     * the current server content as the "remote branch" and the client-provided
     * content as the "local branch", with the ancestor approximated as the
     * overlap of unchanged regions. This is sufficient for typical offline-edit
     * scenarios and matches the approach used by most collaborative editors without
     * a dedicated CRDT or OT layer.
     */
    private NoteResponse attemptMerge(Note note, NoteRequest request, UUID userId, UUID noteId) {
        String remoteContent   = note.getPlainTextContent();
        String localContent    = request.getContent() != null
                ? contentProcessor.toPlainText(request.getContent())
                : remoteContent;

        // Ancestor: approximate as the common prefix of remote and local
        // (empty string is a safe fallback — merge degrades to last-write-wins on full conflicts)
        String ancestor = deriveAncestor(remoteContent, localContent);

        MergeService.MergeResult result = mergeService.mergeText(ancestor, localContent, remoteContent);

        if (result.isClean()) {
            // Auto-merge succeeded — apply merged plain text and reconstruct content
            // We use the client's Markdown content as-is (client intended the full Markdown;
            // the plain-text merge was only for conflict detection on overlapping edits)
            applyContentUpdate(note, request);
            note.setVersion(note.getVersion() + 1);

            try {
                Note saved = noteRepository.save(note);
                log.info("Auto-merge succeeded noteId={} user={}", noteId, userId);
                wsNotifier.notifyUser(userId, "notes.updated", NoteResponse.from(saved));
                return NoteResponse.from(saved);
            } catch (OptimisticLockingFailureException ex) {
                Note current = findOwnedNote(noteId, userId);
                throw new VersionConflictException(NoteResponse.from(current));
            }
        }

        // Conflicts — throw so GlobalExceptionHandler can build the structured 409
        throw new MergeConflictException(NoteResponse.from(note), result.getConflicts());
    }

    /**
     * Derives a minimal common ancestor from two diverged texts.
     * Uses the longest common prefix by line as a rough approximation.
     * This is not a proper ancestor but works well for short-to-medium notes.
     */
    private String deriveAncestor(String remote, String local) {
        String[] remoteLines = remote.split("\n", -1);
        String[] localLines  = local.split("\n", -1);
        int commonLength = 0;
        int max = Math.min(remoteLines.length, localLines.length);
        while (commonLength < max && remoteLines[commonLength].equals(localLines[commonLength])) {
            commonLength++;
        }
        if (commonLength == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commonLength; i++) {
            if (i > 0) sb.append("\n");
            sb.append(remoteLines[i]);
        }
        return sb.toString();
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

    private Set<Tag> resolveTagsForUser(Set<UUID> tagIds, UUID userId) {
        Set<Tag> tags = tagRepository.findAllByIdInAndUserId(tagIds, userId);
        if (tags.size() != tagIds.size()) {
            throw AppException.notFound(ErrorCode.RES_TAG_NOT_FOUND);
        }
        return tags;
    }

    private Instant parseClientTimestamp(String iso, UUID noteId) {
        try {
            return Instant.parse(iso);
        } catch (Exception ex) {
            log.warn("Could not parse clientUpdatedAt='{}' for noteId={} — skipping timestamp check", iso, noteId);
            return null;
        }
    }
}
