package com.oussama_chatri.productivityx.features.sync.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.events.entity.Event;
import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.entity.Note;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.pomodoro.entity.PomodoroSession;
import com.oussama_chatri.productivityx.features.pomodoro.repository.PomodoroSessionRepository;
import com.oussama_chatri.productivityx.features.sync.dto.response.DeltaSyncResponse;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import com.oussama_chatri.productivityx.features.tasks.entity.Task;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cursor-based delta sync.
 *
 * <p>Each entity type uses the query pattern:
 * <pre>
 *   WHERE user_id = :userId
 *     AND updated_at >= :since
 *     AND (updated_at > :cursorUpdatedAt
 *          OR (updated_at = :cursorUpdatedAt AND id > :cursorId))
 *   ORDER BY updated_at ASC, id ASC
 *   LIMIT :perTypeLimit
 * </pre>
 *
 * <p>The cursor encodes {@code <updatedAtEpochMs>:<lastId>} — opaque to the client.
 * On each page the cursor advances to the last item returned across all entity types.
 *
 * <p>All entity types share a single limit budget distributed evenly. Each type gets
 * {@code limit / 4} rows. This keeps the response predictably sized and avoids one
 * entity type monopolizing the page. Clients continue paginating until {@code hasMore}
 * is false on all types.
 *
 * <p>Soft-deleted items (is_deleted = true) are returned with {@code deleted = true}
 * so clients can remove them from local storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncServiceImpl implements SyncService {

    private static final int MAX_SYNC_RANGE_DAYS = 365;

    private final NoteRepository            noteRepository;
    private final TaskRepository            taskRepository;
    private final EventRepository           eventRepository;
    private final PomodoroSessionRepository sessionRepository;
    private final SecurityUtils             securityUtils;

    @Override
    @Transactional(readOnly = true)
    public DeltaSyncResponse delta(Instant since, String cursor, int limit) {
        Instant maxSince = Instant.now().minus(MAX_SYNC_RANGE_DAYS, ChronoUnit.DAYS);
        if (since.isBefore(maxSince)) {
            throw AppException.badRequest(ErrorCode.VAL_SYNC_RANGE_TOO_LARGE);
        }

        UUID userId = securityUtils.currentUserId();

        // Parse cursor — null means first page
        CursorPosition cursorPos = parseCursor(cursor);

        // Distribute limit evenly across 4 entity types (rounded up so we don't under-fetch)
        int perType = (int) Math.ceil((double) limit / 4);

        List<Note>            notes    = noteRepository.findChangedSinceCursor(
                userId, since, cursorPos.updatedAt, cursorPos.id, perType);
        List<Task>            tasks    = taskRepository.findChangedSinceCursor(
                userId, since, cursorPos.updatedAt, cursorPos.id, perType);
        List<Event>           events   = eventRepository.findChangedSinceCursor(
                userId, since, cursorPos.updatedAt, cursorPos.id, perType);
        List<PomodoroSession> sessions = sessionRepository.findCreatedSinceCursor(
                userId, since, cursorPos.updatedAt, cursorPos.id, perType);

        List<NoteResponse>            noteResponses    = notes.stream().map(NoteResponse::from).collect(Collectors.toList());
        List<TaskResponse>            taskResponses    = tasks.stream().map(TaskResponse::from).collect(Collectors.toList());
        List<EventResponse>           eventResponses   = events.stream().map(EventResponse::from).collect(Collectors.toList());
        List<PomodoroSessionResponse> sessionResponses = sessions.stream().map(PomodoroSessionResponse::from).collect(Collectors.toList());

        // hasMore = true if any entity type returned a full page
        boolean hasMore = notes.size() == perType
                || tasks.size() == perType
                || events.size() == perType
                || sessions.size() == perType;

        // Build next cursor from the last item across all types (latest updatedAt + id)
        String nextCursor = hasMore ? buildNextCursor(notes, tasks, events, sessions) : null;

        int total = noteResponses.size() + taskResponses.size() + eventResponses.size() + sessionResponses.size();
        log.debug("Delta sync user={} since={} cursor={} total={} hasMore={}", userId, since, cursor, total, hasMore);

        return DeltaSyncResponse.builder()
                .notes(noteResponses)
                .tasks(taskResponses)
                .events(eventResponses)
                .pomodoroSessions(sessionResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .syncedAt(Instant.now())
                .totalChanges(total)
                .build();
    }

    /**
     * Builds the cursor string from the maximum updatedAt + id across all results.
     * We use the "latest" item so the next page query's WHERE clause picks up from
     * exactly where this page left off.
     */
    private String buildNextCursor(List<Note> notes, List<Task> tasks,
                                   List<Event> events, List<PomodoroSession> sessions) {
        Instant maxUpdatedAt = Instant.EPOCH;
        UUID    maxId        = new UUID(0, 0);

        for (Note n : notes) {
            if (n.getUpdatedAt() != null && n.getUpdatedAt().isAfter(maxUpdatedAt)) {
                maxUpdatedAt = n.getUpdatedAt();
                maxId = n.getId();
            }
        }
        for (Task t : tasks) {
            if (t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(maxUpdatedAt)) {
                maxUpdatedAt = t.getUpdatedAt();
                maxId = t.getId();
            }
        }
        for (Event e : events) {
            if (e.getUpdatedAt() != null && e.getUpdatedAt().isAfter(maxUpdatedAt)) {
                maxUpdatedAt = e.getUpdatedAt();
                maxId = e.getId();
            }
        }
        // PomodoroSession uses createdAt (sessions are immutable after creation)
        for (PomodoroSession s : sessions) {
            if (s.getCreatedAt() != null && s.getCreatedAt().isAfter(maxUpdatedAt)) {
                maxUpdatedAt = s.getCreatedAt();
                maxId = s.getId();
            }
        }

        return maxUpdatedAt.toEpochMilli() + ":" + maxId;
    }

    /**
     * Parses {@code <epochMs>:<uuid>} cursor. Returns a zero-position cursor on null/invalid input
     * which means "start from the beginning of results after since".
     */
    private CursorPosition parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorPosition(Instant.EPOCH, new UUID(0, 0));
        }
        try {
            int colonIdx = cursor.indexOf(':');
            if (colonIdx < 0) return new CursorPosition(Instant.EPOCH, new UUID(0, 0));
            Instant ts = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, colonIdx)));
            UUID id    = UUID.fromString(cursor.substring(colonIdx + 1));
            return new CursorPosition(ts, id);
        } catch (Exception ex) {
            log.warn("Malformed sync cursor='{}' — starting from beginning", cursor);
            return new CursorPosition(Instant.EPOCH, new UUID(0, 0));
        }
    }

    private record CursorPosition(Instant updatedAt, UUID id) {}
}
