package com.oussama_chatri.productivityx.features.preferences.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdatePreferencesRequest {

    @Min(value = 1, message = "Focus duration must be at least 1 minute.")
    @Max(value = 120, message = "Focus duration must not exceed 120 minutes.")
    private Integer pomodoroFocusMinutes;

    @Min(value = 1, message = "Short break must be at least 1 minute.")
    @Max(value = 60, message = "Short break must not exceed 60 minutes.")
    private Integer pomodoroShortBreakMinutes;

    @Min(value = 1, message = "Long break must be at least 1 minute.")
    @Max(value = 60, message = "Long break must not exceed 60 minutes.")
    private Integer pomodoroLongBreakMinutes;

    @Min(value = 1, message = "Cycles must be at least 1.")
    @Max(value = 10, message = "Cycles must not exceed 10.")
    private Integer pomodoroCyclesBeforeLongBreak;

    private Boolean pomodoroAutoStartBreaks;
    private Boolean pomodoroAutoStartFocus;
    private Boolean pomodoroSoundEnabled;

    private Boolean notifyTaskReminders;
    private Boolean notifyEventReminders;
    private Boolean notifyPomodoroEnd;
    private Boolean notifyDailySummary;

    private String defaultTaskView;
    private String defaultTaskSort;
    private Boolean showCompletedTasks;
    private String defaultCalendarView;
    private String weekStartsOn;

    private Boolean aiContextEnabled;
    private String aiModel;

    private Boolean compactMode;
}
