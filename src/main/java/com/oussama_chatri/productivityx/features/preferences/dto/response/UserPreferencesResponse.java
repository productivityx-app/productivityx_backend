package com.oussama_chatri.productivityx.features.preferences.dto.response;

import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserPreferencesResponse {

    private final UUID id;
    private final UUID userId;

    private final int pomodoroFocusMinutes;
    private final int pomodoroShortBreakMinutes;
    private final int pomodoroLongBreakMinutes;
    private final int pomodoroCyclesBeforeLongBreak;
    private final boolean pomodoroAutoStartBreaks;
    private final boolean pomodoroAutoStartFocus;
    private final boolean pomodoroSoundEnabled;

    private final boolean notifyTaskReminders;
    private final boolean notifyEventReminders;
    private final boolean notifyPomodoroEnd;
    private final boolean notifyDailySummary;

    private final String defaultTaskView;
    private final String defaultTaskSort;
    private final boolean showCompletedTasks;
    private final String defaultCalendarView;
    private final String weekStartsOn;

    private final boolean aiContextEnabled;
    private final String aiModel;

    private final boolean compactMode;

    private final Instant updatedAt;

    public static UserPreferencesResponse from(UserPreferences prefs) {
        return UserPreferencesResponse.builder()
                .id(prefs.getId())
                .userId(prefs.getUserId())
                .pomodoroFocusMinutes(prefs.getPomodoroFocusMinutes())
                .pomodoroShortBreakMinutes(prefs.getPomodoroShortBreakMinutes())
                .pomodoroLongBreakMinutes(prefs.getPomodoroLongBreakMinutes())
                .pomodoroCyclesBeforeLongBreak(prefs.getPomodoroCyclesBeforeLongBreak())
                .pomodoroAutoStartBreaks(prefs.isPomodoroAutoStartBreaks())
                .pomodoroAutoStartFocus(prefs.isPomodoroAutoStartFocus())
                .pomodoroSoundEnabled(prefs.isPomodoroSoundEnabled())
                .notifyTaskReminders(prefs.isNotifyTaskReminders())
                .notifyEventReminders(prefs.isNotifyEventReminders())
                .notifyPomodoroEnd(prefs.isNotifyPomodoroEnd())
                .notifyDailySummary(prefs.isNotifyDailySummary())
                .defaultTaskView(prefs.getDefaultTaskView())
                .defaultTaskSort(prefs.getDefaultTaskSort())
                .showCompletedTasks(prefs.isShowCompletedTasks())
                .defaultCalendarView(prefs.getDefaultCalendarView())
                .weekStartsOn(prefs.getWeekStartsOn())
                .aiContextEnabled(prefs.isAiContextEnabled())
                .aiModel(prefs.getAiModel())
                .compactMode(prefs.isCompactMode())
                .updatedAt(prefs.getUpdatedAt())
                .build();
    }
}
