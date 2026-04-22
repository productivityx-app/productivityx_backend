package com.oussama_chatri.productivityx.features.tasks.dto.request;

import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "Status is required.")
    private TaskStatus status;
}
