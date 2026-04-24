package com.oussama_chatri.productivityx.features.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiContext {

    private final int tasksDueToday;
    private final int tasksOverdue;
    private final int totalActiveTasks;
    private final int upcomingEventsThisWeek;
    private final String lastEditedNoteTitle;
    private final String currentPomodoroTask;
    private final int todayFocusMinutes;
}
