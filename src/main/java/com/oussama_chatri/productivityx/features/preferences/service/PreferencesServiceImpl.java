package com.oussama_chatri.productivityx.features.preferences.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.preferences.dto.request.UpdatePreferencesRequest;
import com.oussama_chatri.productivityx.features.preferences.dto.response.UserPreferencesResponse;
import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import com.oussama_chatri.productivityx.features.preferences.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreferencesServiceImpl implements PreferencesService {

    private final UserPreferencesRepository preferencesRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences() {
        UUID userId = securityUtils.currentUserId();
        return UserPreferencesResponse.from(findPreferences(userId));
    }

    @Override
    @Transactional
    public UserPreferencesResponse updatePreferences(UpdatePreferencesRequest request) {
        UUID userId = securityUtils.currentUserId();
        UserPreferences prefs = findPreferences(userId);

        if (request.getPomodoroFocusMinutes() != null)
            prefs.setPomodoroFocusMinutes(request.getPomodoroFocusMinutes());
        if (request.getPomodoroShortBreakMinutes() != null)
            prefs.setPomodoroShortBreakMinutes(request.getPomodoroShortBreakMinutes());
        if (request.getPomodoroLongBreakMinutes() != null)
            prefs.setPomodoroLongBreakMinutes(request.getPomodoroLongBreakMinutes());
        if (request.getPomodoroCyclesBeforeLongBreak() != null)
            prefs.setPomodoroCyclesBeforeLongBreak(request.getPomodoroCyclesBeforeLongBreak());
        if (request.getPomodoroAutoStartBreaks() != null)
            prefs.setPomodoroAutoStartBreaks(request.getPomodoroAutoStartBreaks());
        if (request.getPomodoroAutoStartFocus() != null)
            prefs.setPomodoroAutoStartFocus(request.getPomodoroAutoStartFocus());
        if (request.getPomodoroSoundEnabled() != null)
            prefs.setPomodoroSoundEnabled(request.getPomodoroSoundEnabled());
        if (request.getNotifyTaskReminders() != null)
            prefs.setNotifyTaskReminders(request.getNotifyTaskReminders());
        if (request.getNotifyEventReminders() != null)
            prefs.setNotifyEventReminders(request.getNotifyEventReminders());
        if (request.getNotifyPomodoroEnd() != null)
            prefs.setNotifyPomodoroEnd(request.getNotifyPomodoroEnd());
        if (request.getNotifyDailySummary() != null)
            prefs.setNotifyDailySummary(request.getNotifyDailySummary());
        if (request.getDefaultTaskView() != null)
            prefs.setDefaultTaskView(request.getDefaultTaskView());
        if (request.getDefaultTaskSort() != null)
            prefs.setDefaultTaskSort(request.getDefaultTaskSort());
        if (request.getShowCompletedTasks() != null)
            prefs.setShowCompletedTasks(request.getShowCompletedTasks());
        if (request.getDefaultCalendarView() != null)
            prefs.setDefaultCalendarView(request.getDefaultCalendarView());
        if (request.getWeekStartsOn() != null)
            prefs.setWeekStartsOn(request.getWeekStartsOn());
        if (request.getAiContextEnabled() != null)
            prefs.setAiContextEnabled(request.getAiContextEnabled());
        if (request.getAiModel() != null)
            prefs.setAiModel(request.getAiModel());
        if (request.getCompactMode() != null)
            prefs.setCompactMode(request.getCompactMode());

        return UserPreferencesResponse.from(preferencesRepository.save(prefs));
    }

    private UserPreferences findPreferences(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_USER_NOT_FOUND));
    }
}
