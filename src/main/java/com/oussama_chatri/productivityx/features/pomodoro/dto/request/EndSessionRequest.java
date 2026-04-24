package com.oussama_chatri.productivityx.features.pomodoro.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EndSessionRequest {

    // Actual seconds the user focused — client sends this for accuracy;
    // server also computes it from startedAt for validation
    @Positive(message = "Actual duration must be a positive number of seconds.")
    private Integer actualDurationSeconds;
}
