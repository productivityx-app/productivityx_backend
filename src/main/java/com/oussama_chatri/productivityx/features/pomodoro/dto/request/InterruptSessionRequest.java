package com.oussama_chatri.productivityx.features.pomodoro.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterruptSessionRequest {

    // How many seconds actually elapsed before the interruption
    @Positive(message = "Actual duration must be a positive number of seconds.")
    private Integer actualDurationSeconds;

    // Optional note on why the session was interrupted
    @Size(max = 255, message = "Interrupt reason must not exceed 255 characters.")
    private String interruptReason;
}
