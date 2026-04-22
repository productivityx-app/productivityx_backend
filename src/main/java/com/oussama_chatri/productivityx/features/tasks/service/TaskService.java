package com.oussama_chatri.productivityx.features.tasks.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.enums.TaskPriority;
import com.oussama_chatri.productivityx.core.enums.TaskStatus;
import com.oussama_chatri.productivityx.features.tasks.dto.request.ReorderRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.TaskRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.request.UpdateStatusRequest;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;

import java.util.UUID;

public interface TaskService {

    TaskResponse create(TaskRequest request);

    // Returns full task with subtasks populated
    TaskResponse getById(UUID taskId);

    // parentId non-null → returns subtasks of that parent instead of top-level tasks
    PagedResponse<TaskResponse> list(int page, int size, TaskStatus status,
                                     TaskPriority priority, UUID parentId);

    PagedResponse<TaskResponse> listTrash(int page, int size);

    TaskResponse update(UUID taskId, TaskRequest request);

    // Dedicated status transition — handles completedAt side effect
    TaskResponse updateStatus(UUID taskId, UpdateStatusRequest request);

    // Bulk position update for drag-and-drop reorder
    void reorder(ReorderRequest request);

    // Moves to trash — also trashes all direct subtasks
    TaskResponse softDelete(UUID taskId);

    // Removes from trash — does NOT cascade to subtasks
    TaskResponse restore(UUID taskId);

    // Permanent removal — task must already be in trash
    void hardDelete(UUID taskId);

    // Called by PomodoroService when a linked session ends
    void addActualMinutes(UUID taskId, int minutes);
}
