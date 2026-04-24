package com.oussama_chatri.productivityx.features.ai.dto.request;

import lombok.Builder;
import lombok.Getter;

/**
 * Snapshot of the user's live workspace state injected into every Gemini request.
 * Built by AiContextBuilder from live DB queries — always fresh, never cached.
 *
 * NOTE: This class belongs in the 'dto' package, NOT 'dto.request'.
 * Update the import in AiServiceImpl accordingly:
 *   import com.oussama_chatri.productivityx.features.ai.dto.AiContext;
 */
@Getter
@Builder
public class AiContext {

    private final int    tasksDueToday;
    private final int    tasksOverdue;
    private final int    totalActiveTasks;
    private final int    upcomingEventsThisWeek;
    private final String lastEditedNoteTitle;
    private final String currentPomodoroTask;
    private final int    todayFocusMinutes;
}
