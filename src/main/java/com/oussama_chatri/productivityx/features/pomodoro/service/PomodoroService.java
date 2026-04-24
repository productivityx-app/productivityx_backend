package com.oussama_chatri.productivityx.features.pomodoro.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.EndSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.InterruptSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.request.StartSessionRequest;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroStatsResponse;

import java.util.UUID;

public interface PomodoroService {

    // Starts a new session — rejects if an active session already exists
    PomodoroSessionResponse startSession(StartSessionRequest request);

    // Marks session as completed and logs actual time to the linked task
    PomodoroSessionResponse endSession(UUID sessionId, EndSessionRequest request);

    // Marks session as interrupted — still logs partial actual time to the linked task
    PomodoroSessionResponse interruptSession(UUID sessionId, InterruptSessionRequest request);

    // Returns the currently active session, or null if none is running
    PomodoroSessionResponse getActiveSession();

    // Paginated session history — optionally filtered by taskId
    PagedResponse<PomodoroSessionResponse> listSessions(int page, int size, UUID taskId);

    // Single session by ID
    PomodoroSessionResponse getById(UUID sessionId);

    // Today's stats for the home dashboard and AI context builder
    PomodoroStatsResponse getTodayStats();
}
