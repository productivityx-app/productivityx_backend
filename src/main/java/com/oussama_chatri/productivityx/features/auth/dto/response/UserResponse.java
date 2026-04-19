package com.oussama_chatri.productivityx.features.auth.dto.response;

import com.oussama_chatri.productivityx.core.enums.Gender;
import com.oussama_chatri.productivityx.core.enums.Language;
import com.oussama_chatri.productivityx.core.enums.Theme;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import com.oussama_chatri.productivityx.features.profile.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private final UUID id;
    private final String email;
    private final String username;
    private final String phone;
    private final boolean emailVerified;
    private final Gender gender;
    private final LocalDate birthDate;
    private final Instant lastLoginAt;
    private final Instant createdAt;

    // Profile
    private final String firstName;
    private final String lastName;
    private final String avatarUrl;
    private final String bio;
    private final String timezone;
    private final Language language;
    private final Theme theme;

    // Preferences snapshot
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

    public static UserResponse from(User user, Profile profile, UserPreferences prefs) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .phone(user.getPhone())
                .emailVerified(user.isEmailVerified())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .timezone(profile.getTimezone())
                .language(profile.getLanguage())
                .theme(profile.getTheme())
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
                .build();
    }
}
