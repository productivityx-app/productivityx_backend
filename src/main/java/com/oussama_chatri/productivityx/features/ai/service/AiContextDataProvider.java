package com.oussama_chatri.productivityx.features.ai.service;

import java.util.UUID;

/**
 * Cross-feature data contract for the AI context snapshot.
 * Isolates the AI feature from direct repository dependencies on events and pomodoro.
 */
public interface AiContextDataProvider {

    int countUpcomingEventsThisWeek(UUID userId);

    String lastEditedNoteTitle(UUID userId);

    String currentPomodoroTaskTitle(UUID userId);

    int todayFocusMinutes(UUID userId);
}
