package com.oussama_chatri.productivityx.features.ai.repository;

import com.oussama_chatri.productivityx.features.ai.service.AiContextDataProvider;
import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.pomodoro.repository.PomodoroSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Cross-feature data provider for the AI context snapshot.
 * Reads from events, notes, and pomodoro repositories without creating
 * circular dependencies between those feature packages and the AI feature.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiContextDataProviderImpl implements AiContextDataProvider {

    private final EventRepository           eventRepository;
    private final NoteRepository            noteRepository;
    private final PomodoroSessionRepository pomodoroSessionRepository;

    @Override
    public int countUpcomingEventsThisWeek(UUID userId) {
        try {
            LocalDate today     = LocalDate.now();
            LocalDate endOfWeek = today.plusDays(7);
            Instant   from      = today.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant   to        = endOfWeek.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            return (int) eventRepository.countUpcomingByUserIdAndRange(userId, from, to);
        } catch (Exception ex) {
            log.warn("Could not fetch upcoming events for AI context: {}", ex.getMessage());
            return 0;
        }
    }

    @Override
    public String lastEditedNoteTitle(UUID userId) {
        try {
            var page = noteRepository.findActiveByUserId(userId, PageRequest.of(0, 1));
            return page.isEmpty() ? null : page.getContent().get(0).getTitle();
        } catch (Exception ex) {
            log.warn("Could not fetch last note for AI context: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public String currentPomodoroTaskTitle(UUID userId) {
        try {
            return pomodoroSessionRepository.findCurrentFocusTaskTitle(userId).orElse(null);
        } catch (Exception ex) {
            log.warn("Could not fetch active Pomodoro task for AI context: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public int todayFocusMinutes(UUID userId) {
        try {
            Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
            int totalSeconds   = pomodoroSessionRepository.sumFocusMinutesToday(userId, startOfDay);
            return (int) Math.ceil(totalSeconds / 60.0);
        } catch (Exception ex) {
            log.warn("Could not fetch today's focus minutes for AI context: {}", ex.getMessage());
            return 0;
        }
    }
}
