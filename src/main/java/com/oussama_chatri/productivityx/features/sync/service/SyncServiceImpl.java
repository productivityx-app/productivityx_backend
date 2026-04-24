package com.oussama_chatri.productivityx.features.sync.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.pomodoro.repository.PomodoroSessionRepository;
import com.oussama_chatri.productivityx.features.sync.dto.response.DeltaSyncResponse;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncServiceImpl implements SyncService {

    // Prevent loading the entire history when a client passes a very old timestamp
    private static final int MAX_SYNC_RANGE_DAYS = 30;

    private final NoteRepository            noteRepository;
    private final TaskRepository            taskRepository;
    private final EventRepository           eventRepository;
    private final PomodoroSessionRepository sessionRepository;
    private final SecurityUtils             securityUtils;

    @Override
    @Transactional(readOnly = true)
    public DeltaSyncResponse delta(Instant since) {
        Instant maxSince = Instant.now().minus(MAX_SYNC_RANGE_DAYS, ChronoUnit.DAYS);
        if (since.isBefore(maxSince)) {
            throw AppException.badRequest(ErrorCode.VAL_SYNC_RANGE_TOO_LARGE);
        }

        UUID userId = securityUtils.currentUserId();

        List<NoteResponse> notes = noteRepository
                .findChangedSince(userId, since)
                .stream().map(NoteResponse::from).collect(Collectors.toList());

        List<TaskResponse> tasks = taskRepository
                .findChangedSince(userId, since)
                .stream().map(TaskResponse::from).collect(Collectors.toList());

        List<EventResponse> events = eventRepository
                .findChangedSince(userId, since)
                .stream().map(EventResponse::from).collect(Collectors.toList());

        List<PomodoroSessionResponse> sessions = sessionRepository
                .findCreatedSince(userId, since)
                .stream().map(PomodoroSessionResponse::from).collect(Collectors.toList());

        int total = notes.size() + tasks.size() + events.size() + sessions.size();
        log.debug("Delta sync user={} since={} total={}", userId, since, total);

        return DeltaSyncResponse.builder()
                .notes(notes)
                .tasks(tasks)
                .events(events)
                .pomodoroSessions(sessions)
                .syncedAt(Instant.now())
                .totalChanges(total)
                .build();
    }
}
