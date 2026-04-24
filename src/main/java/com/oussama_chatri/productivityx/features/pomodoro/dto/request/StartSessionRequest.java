package com.oussama_chatri.productivityx.features.pomodoro.dto.request;

import com.oussama_chatri.productivityx.core.enums.PomodoroType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartSessionRequest {

    @NotNull(message = "Session type is required (FOCUS, SHORT_BREAK, LONG_BREAK).")
    private PomodoroType type;

    // Optional — links the session to a task so actual minutes are tracked on that task
    private UUID taskId;
}
