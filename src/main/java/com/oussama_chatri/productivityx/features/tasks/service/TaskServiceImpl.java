package com.oussama_chatri.productivityx.features.tasks.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.PageableUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.tasks.dto.request.ReorderRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.TaskRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.UpdateStatusRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import com.oussama_chatri.productivityx.features.tasks.entity.Task;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import com.oussama_chatri.productivityx.shared.websocket.WebSocketNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private static final int MAX_SUBTASK_DEPTH = 1; // parent → child only; grandchildren are forbidden

    private final TaskRepository    taskRepository;
    private final SecurityUtils     securityUtils;
    private final PageableUtils     pageableUtils;
    private final WebSocketNotifier wsNotifier;

    // Create

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request) {
        User user   = securityUtils.currentUser();
        Task task   = buildTask(user, request);
        Task saved  = taskRepository.save(task);

        wsNotifier.notifyUser(user.getId(), "tasks.created", TaskResponse.from(saved));
        log.debug("Task created id={} user={}", saved.getId(), user.getId());
        return TaskResponse.from(saved);
    }

    // Read

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(UUID taskId) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTaskWithSubtasks(taskId, userId);
        return TaskResponse.fromWithSubtasks(task);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> list(int page, int size, TaskStatus status,
                                            TaskPriority priority, UUID parentId) {
        UUID     userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);

        Page<Task> taskPage;

        if (parentId != null) {
            // Subtasks of a specific parent — returned as a paged list for consistency
            List<Task> subtasks = taskRepository.findActiveSubtasksByParentId(parentId);
            // Manual paging for the subtask list (typically small — no DB-level pagination needed)
            int start = (int) pageable.getOffset();
            int end   = Math.min(start + pageable.getPageSize(), subtasks.size());
            List<Task> pageContent = start >= subtasks.size() ? List.of() : subtasks.subList(start, end);
            List<TaskResponse> responses = pageContent.stream()
                    .map(TaskResponse::from)
                    .collect(Collectors.toList());
            return PagedResponse.<TaskResponse>builder()
                    .content(responses)
                    .page(page)
                    .size(size)
                    .totalElements(subtasks.size())
                    .totalPages((int) Math.ceil((double) subtasks.size() / size))
                    .first(page == 0)
                    .last(end >= subtasks.size())
                    .build();
        }

        if (status != null) {
            taskPage = taskRepository.findTopLevelActiveByUserIdAndStatus(userId, status, pageable);
        } else if (priority != null) {
            taskPage = taskRepository.findTopLevelActiveByUserIdAndPriority(userId, priority, pageable);
        } else {
            taskPage = taskRepository.findTopLevelActiveByUserId(userId, pageable);
        }

        return pageableUtils.toPagedResponse(taskPage.map(TaskResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listTrash(int page, int size) {
        UUID userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);
        return pageableUtils.toPagedResponse(
                taskRepository.findDeletedByUserId(userId, pageable).map(TaskResponse::from));
    }

    // Update

    @Override
    @Transactional
    public TaskResponse update(UUID taskId, TaskRequest request) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTask(taskId, userId);

        if (task.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_TASK_NOT_FOUND,
                    "Cannot edit a trashed task. Restore it first.");
        }

        applyUpdate(task, request, userId);
        task.setVersion(task.getVersion() + 1);

        Task saved = taskRepository.save(task);
        wsNotifier.notifyUser(userId, "tasks.updated", TaskResponse.from(saved));
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional
    public TaskResponse updateStatus(UUID taskId, UpdateStatusRequest request) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTask(taskId, userId);

        if (task.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_TASK_NOT_FOUND,
                    "Cannot update status of a trashed task.");
        }

        TaskStatus newStatus = request.getStatus();
        task.setStatus(newStatus);

        // Manage completedAt timestamp as part of the status transition
        if (newStatus == TaskStatus.DONE) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(Instant.now());
            }
        } else {
            task.setCompletedAt(null);
        }

        task.setVersion(task.getVersion() + 1);

        Task saved = taskRepository.save(task);
        wsNotifier.notifyUser(userId, "tasks.updated", TaskResponse.from(saved));
        log.debug("Task status updated id={} status={}", taskId, newStatus);
        return TaskResponse.from(saved);
    }

    // Reorder

    @Override
    @Transactional
    public void reorder(ReorderRequest request) {
        UUID userId = securityUtils.currentUserId();

        List<UUID> ids = request.getItems().stream()
                .map(ReorderRequest.ReorderItem::getId)
                .collect(Collectors.toList());

        // Single ownership batch check — avoids N individual ownership queries
        long ownedCount = taskRepository.countByIdsAndUserId(ids, userId);
        if (ownedCount != ids.size()) {
            throw AppException.forbidden();
        }

        for (ReorderRequest.ReorderItem item : request.getItems()) {
            Task task = taskRepository.findByIdAndUserId(item.getId(), userId)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TASK_NOT_FOUND));
            task.setPosition(item.getPosition());
            taskRepository.save(task);
        }

        log.debug("Reorder applied for {} tasks by user={}", ids.size(), userId);
    }

    // Soft delete / restore / hard delete

    @Override
    @Transactional
    public TaskResponse softDelete(UUID taskId) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTask(taskId, userId);

        task.setDeleted(true);
        task.setDeletedAt(Instant.now());

        // Cascade soft-delete to direct subtasks in the same transaction
        List<Task> subtasks = taskRepository.findActiveSubtasksByParentId(taskId);
        for (Task sub : subtasks) {
            sub.setDeleted(true);
            sub.setDeletedAt(Instant.now());
            taskRepository.save(sub);
        }

        Task saved = taskRepository.save(task);
        wsNotifier.notifyUser(userId, "tasks.deleted", TaskResponse.from(saved));
        log.debug("Task soft-deleted id={} subtasksCascaded={}", taskId, subtasks.size());
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional
    public TaskResponse restore(UUID taskId) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTask(taskId, userId);

        if (!task.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_TASK_NOT_FOUND, "Task is not in trash.");
        }

        task.setDeleted(false);
        task.setDeletedAt(null);

        Task saved = taskRepository.save(task);
        wsNotifier.notifyUser(userId, "tasks.restored", TaskResponse.from(saved));
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional
    public void hardDelete(UUID taskId) {
        UUID userId = securityUtils.currentUserId();
        Task task   = findOwnedTask(taskId, userId);

        if (!task.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_TASK_NOT_FOUND,
                    "Move the task to trash before permanently deleting it.");
        }

        taskRepository.delete(task);
        wsNotifier.notifyUser(userId, "tasks.deleted", taskId);
        log.debug("Task permanently deleted id={} user={}", taskId, userId);
    }

    // Pomodoro integration

    @Override
    @Transactional
    public void addActualMinutes(UUID taskId, int minutes) {
        if (minutes <= 0) return;

        taskRepository.incrementActualMinutes(taskId, minutes);

        // Push an update so clients refresh the task card's actual time display
        taskRepository.findById(taskId).ifPresent(task ->
                wsNotifier.notifyUser(task.getUserId(), "tasks.updated", TaskResponse.from(task)));

        log.debug("Added {} actual minutes to task id={}", minutes, taskId);
    }

    // Private helpers

    private Task buildTask(User user, TaskRequest request) {
        Task.TaskBuilder builder = Task.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
                .dueDate(request.getDueDate())
                .dueTime(request.getDueTime())
                .reminderAt(request.getReminderAt())
                .estimatedMinutes(request.getEstimatedMinutes())
                .linkedEventId(request.getLinkedEventId())
                .position(request.getPosition() != null ? request.getPosition() : 0);

        if (request.getParentTaskId() != null) {
            Task parent = findOwnedTask(request.getParentTaskId(), user.getId());

            // Enforce max subtask depth — grandchildren are forbidden
            if (parent.getParentTask() != null) {
                throw AppException.badRequest(ErrorCode.VAL_SUBTASK_DEPTH_EXCEEDED);
            }

            builder.parentTask(parent);
        }

        return builder.build();
    }

    private void applyUpdate(Task task, TaskRequest request, UUID userId) {
        if (request.getTitle() != null)           task.setTitle(request.getTitle().trim());
        if (request.getDescription() != null)     task.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            // Status change via full update also handles completedAt
            if (request.getStatus() == TaskStatus.DONE && task.getCompletedAt() == null) {
                task.setCompletedAt(Instant.now());
            } else if (request.getStatus() != TaskStatus.DONE) {
                task.setCompletedAt(null);
            }
            task.setStatus(request.getStatus());
        }
        if (request.getPriority() != null)        task.setPriority(request.getPriority());
        if (request.getDueDate() != null)         task.setDueDate(request.getDueDate());
        if (request.getDueTime() != null)         task.setDueTime(request.getDueTime());
        if (request.getReminderAt() != null)      task.setReminderAt(request.getReminderAt());
        if (request.getEstimatedMinutes() != null) task.setEstimatedMinutes(request.getEstimatedMinutes());
        if (request.getLinkedEventId() != null)   task.setLinkedEventId(request.getLinkedEventId());
        if (request.getPosition() != null)        task.setPosition(request.getPosition());

        if (request.getParentTaskId() != null
                && (task.getParentTask() == null
                    || !task.getParentTask().getId().equals(request.getParentTaskId()))) {
            Task newParent = findOwnedTask(request.getParentTaskId(), userId);
            if (newParent.getParentTask() != null) {
                throw AppException.badRequest(ErrorCode.VAL_SUBTASK_DEPTH_EXCEEDED);
            }
            task.setParentTask(newParent);
        }
    }

    private Task findOwnedTask(UUID taskId, UUID userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TASK_NOT_FOUND));
    }

    private Task findOwnedTaskWithSubtasks(UUID taskId, UUID userId) {
        return taskRepository.findByIdAndUserIdWithSubtasks(taskId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_TASK_NOT_FOUND));
    }
}
