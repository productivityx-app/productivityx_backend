package com.oussama_chatri.productivityx.features.tasks.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.features.tasks.dto.request.ReorderRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.TaskRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.UpdateStatusRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import com.oussama_chatri.productivityx.features.tasks.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management — CRUD, subtasks, kanban status, drag-drop reorder")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Create a task or subtask",
               description = "Pass parentTaskId to create a subtask (max 2 levels deep). " +
                             "Omit it for a top-level task.")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(taskService.create(request), "Task created."));
    }

    @GetMapping
    @Operation(summary = "List tasks",
               description = "All top-level tasks by default. " +
                             "Pass parentId to list subtasks. " +
                             "Filter by status or priority as needed.")
    public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by status: TODO, IN_PROGRESS, ON_HOLD, DONE, CANCELLED")
            @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Filter by priority: LOW, MEDIUM, HIGH, URGENT")
            @RequestParam(required = false) TaskPriority priority,
            @Parameter(description = "Return subtasks of this parent task ID instead of top-level tasks")
            @RequestParam(required = false) UUID parentId) {
        return ResponseEntity.ok(
                ApiResponse.ok(taskService.list(page, size, status, priority, parentId)));
    }

    @GetMapping("/trash")
    @Operation(summary = "List trashed tasks",
               description = "Top-level tasks in the trash only. Subtasks are trashed implicitly.")
    public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> listTrash(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.listTrash(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single task with its subtasks")
    public ResponseEntity<ApiResponse<TaskResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full update of a task",
               description = "Only non-null request fields are applied. " +
                             "Status change via this endpoint also manages completedAt.")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.update(id, request), "Task updated."));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status",
               description = "Dedicated status transition endpoint. " +
                             "Automatically sets completedAt when transitioning to DONE, " +
                             "and clears it when transitioning away.")
    public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(taskService.updateStatus(id, request), "Status updated."));
    }

    @PatchMapping("/reorder")
    @Operation(summary = "Bulk reorder tasks",
               description = "Accepts a list of {id, position} pairs. " +
                             "All IDs must belong to the authenticated user. " +
                             "Used for kanban drag-and-drop and list reordering.")
    public ResponseEntity<ApiResponse<Void>> reorder(@Valid @RequestBody ReorderRequest request) {
        taskService.reorder(request);
        return ResponseEntity.ok(ApiResponse.message("Tasks reordered."));
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "Restore a task from trash",
               description = "Does not cascade to subtasks — restore each subtask explicitly.")
    public ResponseEntity<ApiResponse<TaskResponse>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(taskService.restore(id), "Task restored."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a task",
               description = "Moves the task and all its direct subtasks to the trash.")
    public ResponseEntity<ApiResponse<TaskResponse>> softDelete(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(taskService.softDelete(id), "Task moved to trash."));
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete a task",
               description = "The task must already be in the trash. " +
                             "Subtasks are removed by cascade (ON DELETE CASCADE in DB).")
    public ResponseEntity<ApiResponse<Void>> hardDelete(@PathVariable UUID id) {
        taskService.hardDelete(id);
        return ResponseEntity.ok(ApiResponse.message("Task permanently deleted."));
    }
}
