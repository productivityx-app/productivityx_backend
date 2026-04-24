package com.oussama_chatri.productivityx.features.ai.service;

import com.oussama_chatri.productivityx.features.ai.dto.response.AiContext;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Assembles the AiContext snapshot from the authenticated user's live workspace data.
 * All queries hit the same DB — no stale caching. Cross-feature data (events, pomodoro)
 * is accessed through AiContextDataProvider to keep feature packages decoupled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiContextBuilder {

    private final TaskRepository       taskRepository;
    private final NoteRepository       noteRepository;
    private final AiContextDataProvider dataProvider;

    public AiContext build(UUID userId) {
        LocalDate today = LocalDate.now();

        long totalActive = taskRepository.countActiveByUserId(userId);
        long dueToday    = taskRepository.countDueTodayByUserId(userId, today);
        long overdue     = taskRepository.countOverdueByUserId(userId, today);

        return AiContext.builder()
                .totalActiveTasks((int) totalActive)
                .tasksDueToday((int) dueToday)
                .tasksOverdue((int) overdue)
                .upcomingEventsThisWeek(dataProvider.countUpcomingEventsThisWeek(userId))
                .lastEditedNoteTitle(dataProvider.lastEditedNoteTitle(userId))
                .currentPomodoroTask(dataProvider.currentPomodoroTaskTitle(userId))
                .todayFocusMinutes(dataProvider.todayFocusMinutes(userId))
                .build();
    }
}
