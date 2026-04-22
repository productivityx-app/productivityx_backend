package com.oussama_chatri.productivityx.features.tasks.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oussama_chatri.productivityx.core.enums.SyncStatus;
import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.features.tasks.entity.Task;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {

    private final UUID id;
    private final UUID userId;

    // Null for top-level tasks
    private final UUID parentTaskId;

    private final UUID linkedEventId;

    private final String title;
    private final String description;

    private final TaskStatus status;
    private final TaskPriority priority;

    private final LocalDate dueDate;
    private final LocalTime dueTime;
    private final Instant reminderAt;

    private final Integer estimatedMinutes;
    private final int actualMinutes;

    private final Instant completedAt;

    private final int position;

    private final boolean deleted;
    private final Instant deletedAt;

    private final int version;
    private final SyncStatus syncStatus;

    // Populated only on single-task (getById) queries — null on list responses
    private final List<TaskResponse> subtasks;

    private final Instant createdAt;
    private final Instant updatedAt;

    // Lightweight factory — subtasks NOT included (used for list / page responses)
    public static TaskResponse from(Task task) {
        return builder()
                .id(task.getId())
                .userId(task.getUserId())
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .linkedEventId(task.getLinkedEventId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .dueTime(task.getDueTime())
                .reminderAt(task.getReminderAt())
                .estimatedMinutes(task.getEstimatedMinutes())
                .actualMinutes(task.getActualMinutes())
                .completedAt(task.getCompletedAt())
                .position(task.getPosition())
                .deleted(task.isDeleted())
                .deletedAt(task.getDeletedAt())
                .version(task.getVersion())
                .syncStatus(task.getSyncStatus())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    // Full factory — includes subtasks (used for single-task detail responses)
    public static TaskResponse fromWithSubtasks(Task task) {
        List<TaskResponse> subtaskResponses = task.getSubtasks().stream()
                .filter(s -> !s.isDeleted())
                .map(TaskResponse::from)
                .collect(Collectors.toList());

        return builder()
                .id(task.getId())
                .userId(task.getUserId())
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .linkedEventId(task.getLinkedEventId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .dueTime(task.getDueTime())
                .reminderAt(task.getReminderAt())
                .estimatedMinutes(task.getEstimatedMinutes())
                .actualMinutes(task.getActualMinutes())
                .completedAt(task.getCompletedAt())
                .position(task.getPosition())
                .deleted(task.isDeleted())
                .deletedAt(task.getDeletedAt())
                .version(task.getVersion())
                .syncStatus(task.getSyncStatus())
                .subtasks(subtaskResponses)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
