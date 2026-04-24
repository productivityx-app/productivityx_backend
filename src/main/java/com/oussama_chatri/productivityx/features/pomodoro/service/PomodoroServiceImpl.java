package com.oussama_chatri.productivityx.features.pomodoro.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.enums.PomodoroType;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.PageableUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.EndSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.InterruptSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.StartSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroStatsResponse;
import com.oussama_chatri.productivityx.features.pomodoro.entity.PomodoroSession;
import com.oussama_chatri.productivityx.features.pomodoro.repository.PomodoroSessionRepository;
import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import com.oussama_chatri.productivityx.features.preferences.repository.UserPreferencesRepository;
import com.oussama_chatri.productivityx.features.tasks.service.TaskService;
import com.oussama_chatri.productivityx.shared.websocket.WebSocketNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PomodoroServiceImpl implements PomodoroService {

    private final PomodoroSessionRepository sessionRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final TaskService               taskService;
    private final SecurityUtils             securityUtils;
    private final PageableUtils             pageableUtils;
    private final WebSocketNotifier         wsNotifier;

    // Start

    @Override
    @Transactional
    public PomodoroSessionResponse startSession(StartSessionRequest request) {
        User user = securityUtils.currentUser();

        // Enforce single active session per user
        sessionRepository.findActiveByUserId(user.getId()).ifPresent(active -> {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "You already have an active session. End it before starting a new one.");
        });

        // Optionally validate taskId ownership — we do a lightweight existence check
        // without loading the full Task entity to keep this service decoupled
        if (request.getTaskId() != null) {
            validateTaskOwnership(request.getTaskId(), user.getId());
        }

        UserPreferences prefs = findPreferences(user.getId());
        int plannedSeconds    = resolvedPlannedSeconds(request.getType(), prefs);

        PomodoroSession session = PomodoroSession.builder()
                .user(user)
                .taskId(request.getTaskId())
                .type(request.getType())
                .plannedDurationSeconds(plannedSeconds)
                .focusMinutesSetting(prefs.getPomodoroFocusMinutes())
                .shortBreakMinutesSetting(prefs.getPomodoroShortBreakMinutes())
                .longBreakMinutesSetting(prefs.getPomodoroLongBreakMinutes())
                .startedAt(Instant.now())
                .build();

        PomodoroSession saved = sessionRepository.save(session);

        wsNotifier.notifyUser(user.getId(), "pomodoro.started", PomodoroSessionResponse.from(saved));
        log.debug("Pomodoro session started id={} type={} user={}", saved.getId(), saved.getType(), user.getId());
        return PomodoroSessionResponse.from(saved);
    }

    // End (completed)

    @Override
    @Transactional
    public PomodoroSessionResponse endSession(UUID sessionId, EndSessionRequest request) {
        UUID userId = securityUtils.currentUserId();
        PomodoroSession session = findOwnedSession(sessionId, userId);

        assertSessionIsActive(session);

        Instant now = Instant.now();
        int actualSeconds = resolveActualSeconds(request.getActualDurationSeconds(), session.getStartedAt(), now);

        session.setActualDurationSeconds(actualSeconds);
        session.setEndedAt(now);
        session.setCompleted(true);

        PomodoroSession saved = sessionRepository.save(session);

        // Credit actual minutes to the linked task
        if (session.getType() == PomodoroType.FOCUS && session.getTaskId() != null) {
            int minutes = (int) Math.ceil(actualSeconds / 60.0);
            taskService.addActualMinutes(session.getTaskId(), minutes);
        }

        wsNotifier.notifyUser(userId, "pomodoro.completed", PomodoroSessionResponse.from(saved));
        log.debug("Pomodoro session completed id={} actualSeconds={} user={}", sessionId, actualSeconds, userId);
        return PomodoroSessionResponse.from(saved);
    }

    // Interrupt

    @Override
    @Transactional
    public PomodoroSessionResponse interruptSession(UUID sessionId, InterruptSessionRequest request) {
        UUID userId = securityUtils.currentUserId();
        PomodoroSession session = findOwnedSession(sessionId, userId);

        assertSessionIsActive(session);

        Instant now = Instant.now();
        int actualSeconds = resolveActualSeconds(request.getActualDurationSeconds(), session.getStartedAt(), now);

        session.setActualDurationSeconds(actualSeconds);
        session.setEndedAt(now);
        session.setCompleted(false);
        session.setInterrupted(true);
        session.setInterruptReason(request.getInterruptReason());

        PomodoroSession saved = sessionRepository.save(session);

        // Still credit partial focus time to the linked task
        if (session.getType() == PomodoroType.FOCUS
                && session.getTaskId() != null
                && actualSeconds > 0) {
            int minutes = (int) Math.ceil(actualSeconds / 60.0);
            taskService.addActualMinutes(session.getTaskId(), minutes);
        }

        wsNotifier.notifyUser(userId, "pomodoro.interrupted", PomodoroSessionResponse.from(saved));
        log.debug("Pomodoro session interrupted id={} reason='{}' user={}",
                sessionId, request.getInterruptReason(), userId);
        return PomodoroSessionResponse.from(saved);
    }

    // Active session

    @Override
    @Transactional(readOnly = true)
    public PomodoroSessionResponse getActiveSession() {
        UUID userId = securityUtils.currentUserId();
        return sessionRepository.findActiveByUserId(userId)
                .map(PomodoroSessionResponse::from)
                .orElse(null);
    }

    // History

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PomodoroSessionResponse> listSessions(int page, int size, UUID taskId) {
        UUID     userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);

        if (taskId != null) {
            return pageableUtils.toPagedResponse(
                    sessionRepository.findByUserIdAndTaskId(userId, taskId, pageable)
                            .map(PomodoroSessionResponse::from));
        }

        return pageableUtils.toPagedResponse(
                sessionRepository.findByUserIdOrderByStartedAtDesc(userId, pageable)
                        .map(PomodoroSessionResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PomodoroSessionResponse getById(UUID sessionId) {
        UUID userId = securityUtils.currentUserId();
        return PomodoroSessionResponse.from(findOwnedSession(sessionId, userId));
    }

    // Today's stats

    @Override
    @Transactional(readOnly = true)
    public PomodoroStatsResponse getTodayStats() {
        UUID    userId   = securityUtils.currentUserId();
        Instant dayStart = todayStart();
        Instant dayEnd   = dayStart.plusSeconds(86_400);

        long completedSessions = sessionRepository.countCompletedFocusTodayByUserId(userId, dayStart, dayEnd);
        long totalFocusSeconds = sessionRepository.sumActualFocusSecondsToday(userId, dayStart, dayEnd);

        return PomodoroStatsResponse.builder()
                .completedFocusSessionsToday(completedSessions)
                .totalFocusSecondsToday(totalFocusSeconds)
                .totalFocusMinutesToday((long) Math.ceil(totalFocusSeconds / 60.0))
                .build();
    }

    // Private helpers

    private PomodoroSession findOwnedSession(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_POMODORO_SESSION_NOT_FOUND));
    }

    private UserPreferences findPreferences(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_USER_NOT_FOUND));
    }

    private void assertSessionIsActive(PomodoroSession session) {
        if (session.getEndedAt() != null) {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "This session has already ended.");
        }
    }

    private int resolvedPlannedSeconds(PomodoroType type, UserPreferences prefs) {
        return switch (type) {
            case FOCUS       -> prefs.getPomodoroFocusMinutes()      * 60;
            case SHORT_BREAK -> prefs.getPomodoroShortBreakMinutes() * 60;
            case LONG_BREAK  -> prefs.getPomodoroLongBreakMinutes()  * 60;
        };
    }

    /**
     * Prefers the client-supplied value when present and plausible.
     * Falls back to server-computed elapsed time to guard against clients
     * sending inflated values.
     */
    private int resolveActualSeconds(Integer clientValue, Instant startedAt, Instant now) {
        int serverComputed = (int) (now.getEpochSecond() - startedAt.getEpochSecond());
        if (clientValue == null || clientValue <= 0) return Math.max(serverComputed, 0);
        // Accept client value only if within a 30-second tolerance of server time
        if (Math.abs(clientValue - serverComputed) <= 30) return clientValue;
        return serverComputed;
    }

    private void validateTaskOwnership(UUID taskId, UUID userId) {
        // Lightweight ownership check — TaskService.addActualMinutes already guards the DB write
        // but we want to surface a 404 immediately at session start rather than at session end
        try {
            taskService.getById(taskId);
        } catch (AppException ex) {
            throw AppException.notFound(ErrorCode.RES_TASK_NOT_FOUND);
        }
    }

    private Instant todayStart() {
        ZonedDateTime startOfDay = ZonedDateTime.now(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC);
        return startOfDay.toInstant();
    }
}
