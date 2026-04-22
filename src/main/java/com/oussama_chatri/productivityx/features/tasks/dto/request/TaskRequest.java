package com.oussama_chatri.productivityx.features.tasks.dto.request;

import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class TaskRequest {

    @NotBlank(message = "Task title is required.")
    @Size(max = 500, message = "Title must not exceed 500 characters.")
    private String title;

    // Plain text or Markdown description — no server-side size cap; TEXT column in DB
    private String description;

    // Nullable on create — defaults to TODO in service layer
    private TaskStatus status;

    // Nullable on create — defaults to MEDIUM in service layer
    private TaskPriority priority;

    private LocalDate dueDate;

    // Only meaningful when dueDate is also set
    private LocalTime dueTime;

    private Instant reminderAt;

    @Positive(message = "Estimated minutes must be a positive number.")
    private Integer estimatedMinutes;

    // Null means top-level task; non-null means subtask
    private UUID parentTaskId;

    // Links this task to a calendar event — validated for ownership in service
    private UUID linkedEventId;

    // Explicit position for client-driven ordering on create; service assigns 0 if absent
    private Integer position;
}
