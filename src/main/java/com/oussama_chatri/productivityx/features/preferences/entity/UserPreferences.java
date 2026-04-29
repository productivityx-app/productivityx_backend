package com.oussama_chatri.productivityx.features.preferences.entity;

import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    // Pomodoro
    @Column(name = "pomodoro_focus_minutes", nullable = false)
    @Builder.Default
    private int pomodoroFocusMinutes = 25;

    @Column(name = "pomodoro_short_break_minutes", nullable = false)
    @Builder.Default
    private int pomodoroShortBreakMinutes = 5;

    @Column(name = "pomodoro_long_break_minutes", nullable = false)
    @Builder.Default
    private int pomodoroLongBreakMinutes = 15;

    @Column(name = "pomodoro_cycles_before_long_break", nullable = false)
    @Builder.Default
    private int pomodoroCyclesBeforeLongBreak = 4;

    @Column(name = "pomodoro_auto_start_breaks", nullable = false)
    @Builder.Default
    private boolean pomodoroAutoStartBreaks = false;

    @Column(name = "pomodoro_auto_start_focus", nullable = false)
    @Builder.Default
    private boolean pomodoroAutoStartFocus = false;

    @Column(name = "pomodoro_sound_enabled", nullable = false)
    @Builder.Default
    private boolean pomodoroSoundEnabled = true;

    // Notifications
    @Column(name = "notify_task_reminders", nullable = false)
    @Builder.Default
    private boolean notifyTaskReminders = true;

    @Column(name = "notify_event_reminders", nullable = false)
    @Builder.Default
    private boolean notifyEventReminders = true;

    @Column(name = "notify_pomodoro_end", nullable = false)
    @Builder.Default
    private boolean notifyPomodoroEnd = true;

    @Column(name = "notify_daily_summary", nullable = false)
    @Builder.Default
    private boolean notifyDailySummary = false;

    // Views
    @Column(name = "default_task_view", nullable = false, length = 10)
    @Builder.Default
    private String defaultTaskView = "LIST";

    @Column(name = "default_task_sort", nullable = false, length = 20)
    @Builder.Default
    private String defaultTaskSort = "DUE_DATE";

    @Column(name = "show_completed_tasks", nullable = false)
    @Builder.Default
    private boolean showCompletedTasks = false;

    @Column(name = "default_calendar_view", nullable = false, length = 10)
    @Builder.Default
    private String defaultCalendarView = "WEEK";

    @Column(name = "week_starts_on", nullable = false, length = 3)
    @Builder.Default
    private String weekStartsOn = "MON";

    // AI
    @Column(name = "ai_context_enabled", nullable = false)
    @Builder.Default
    private boolean aiContextEnabled = true;

    @Column(name = "ai_model", nullable = false, length = 50)
    @Builder.Default
    private String aiModel = "llama-3.3-70b-versatile";

    // Display
    @Column(name = "compact_mode", nullable = false)
    @Builder.Default
    private boolean compactMode = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
