package com.oussama_chatri.productivityx.features.sync.dto.response;

import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Delta sync response with cursor-based pagination.
 *
 * <p>Pagination flow:
 * <ol>
 *   <li>Client calls {@code GET /api/v1/sync/delta?since=<ISO>&limit=100}
 *       (no cursor on first page).</li>
 *   <li>If {@code hasMore == true}, client calls again with {@code cursor=<nextCursor>}.</li>
 *   <li>Client repeats until {@code hasMore == false}.</li>
 *   <li>Client stores {@code syncedAt} from the last page as its new {@code since} value.</li>
 * </ol>
 *
 * <p>Soft-deleted entities are included with their {@code deleted} flag set to {@code true}
 * so clients can remove them from local storage.
 */
@Getter
@Builder
public class DeltaSyncResponse {

    private final List<NoteResponse>            notes;
    private final List<TaskResponse>            tasks;
    private final List<EventResponse>           events;
    private final List<PomodoroSessionResponse> pomodoroSessions;

    /**
     * Opaque cursor for the next page — pass as {@code cursor} query param.
     * Format: {@code <updatedAtEpochMs>:<entityId>} — the last entity's position.
     * Null when there are no more pages.
     */
    private final String  nextCursor;

    /** True when there are more items beyond this page. */
    private final boolean hasMore;

    /**
     * Server's current timestamp — client stores this as the new {@code since} value
     * after all pages are consumed.
     */
    private final Instant syncedAt;

    /** Total items in this page (not the total across all pages). */
    private final int totalChanges;
}
