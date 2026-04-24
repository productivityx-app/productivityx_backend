package com.oussama_chatri.productivityx.features.pomodoro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oussama_chatri.productivityx.core.enums.PomodoroType;
import com.oussama_chatri.productivityx.features.pomodoro.entity.PomodoroSession;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PomodoroSessionResponse {

    private final UUID id;
    private final UUID userId;
    private final UUID taskId;

    private final PomodoroType type;

    private final int plannedDurationSeconds;
    private final Integer actualDurationSeconds;

    private final boolean interrupted;
    private final String interruptReason;

    // Settings snapshot so the client can display what settings were in effect
    private final int focusMinutesSetting;
    private final int shortBreakMinutesSetting;
    private final int longBreakMinutesSetting;

    private final Instant startedAt;
    private final Instant endedAt;

    private final boolean completed;

    // Convenience field — actual focus minutes rounded up for display
    private final Integer actualMinutes;

    private final Instant createdAt;

    public static PomodoroSessionResponse from(PomodoroSession session) {
        Integer actualMinutes = session.getActualDurationSeconds() != null
                ? (int) Math.ceil(session.getActualDurationSeconds() / 60.0)
                : null;

        return PomodoroSessionResponse.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .taskId(session.getTaskId())
                .type(session.getType())
                .plannedDurationSeconds(session.getPlannedDurationSeconds())
                .actualDurationSeconds(session.getActualDurationSeconds())
                .interrupted(session.isInterrupted())
                .interruptReason(session.getInterruptReason())
                .focusMinutesSetting(session.getFocusMinutesSetting())
                .shortBreakMinutesSetting(session.getShortBreakMinutesSetting())
                .longBreakMinutesSetting(session.getLongBreakMinutesSetting())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .completed(session.isCompleted())
                .actualMinutes(actualMinutes)
                .createdAt(session.getCreatedAt())
                .build();
    }
}
