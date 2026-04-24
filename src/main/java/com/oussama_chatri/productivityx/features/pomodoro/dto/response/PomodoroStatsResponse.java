package com.oussama_chatri.productivityx.features.pomodoro.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PomodoroStatsResponse {

    private final long completedFocusSessionsToday;
    private final long totalFocusMinutesToday;
    private final long totalFocusSecondsToday;
}
