package com.taskflow.backend.services;

import com.taskflow.backend.dto.CreateTaskRequest;
import com.taskflow.backend.dto.PaginatedResponse;
import com.taskflow.backend.dto.TaskResponse;
import com.taskflow.backend.dto.UpdateTaskRequest;
import com.taskflow.backend.entities.Project;
import com.taskflow.backend.entities.Task;
import com.taskflow.backend.entities.User;
import com.taskflow.backend.exception.ForbiddenActionException;
import com.taskflow.backend.exception.FieldValidationException;
import com.taskflow.backend.exception.ResourceNotFoundException;
import com.taskflow.backend.repositories.ProjectRepository;
import com.taskflow.backend.repositories.TaskRepository;
import com.taskflow.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;

    private static final Set<String> ALLOWED_STATUSES = Set.of("todo", "in_progress", "done");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("low", "medium", "high");

    public PaginatedResponse<TaskResponse> listTasks(UUID projectId, String status, UUID assigneeId,
                                                     int page, int limit) {
        if (!projectRepo.existsById(projectId)) {
            throw new ResourceNotFoundException("not found");
        }

        var pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        var result = taskRepo.findByProjectWithFiltersPaged(projectId, status, assigneeId, pageable);

        List<TaskResponse> items = result.getContent().stream()
                .map(TaskService::mapToResponse)
                .toList();

        return PaginatedResponse.<TaskResponse>builder()
                .data(items)
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public TaskResponse createTask(UUID projectId, CreateTaskRequest req, User creator) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("not found"));

        String priority = (req.getPriority() != null) ? req.getPriority() : "medium";

        User assignee = resolveAssignee(req.getAssigneeId());

        Task task = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .status("todo")
                .priority(priority)
                .project(project)
                .assignee(assignee)
                .createdBy(creator)
                .dueDate(req.getDueDate())
                .build();

        taskRepo.save(task);
        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest req, User currentUser) {
        Task task = taskRepo.findByIdWithRelations(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("not found"));

        Map<String, String> errors = new LinkedHashMap<>();
        if (req.getStatus() != null && !ALLOWED_STATUSES.contains(req.getStatus())) {
            errors.put("status", "must be todo, in_progress, or done");
        }
        if (req.getPriority() != null && !ALLOWED_PRIORITIES.contains(req.getPriority())) {
            errors.put("priority", "must be low, medium, or high");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        if (req.getTitle() != null) task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getStatus() != null) task.setStatus(req.getStatus());
        if (req.getPriority() != null) task.setPriority(req.getPriority());
        if (req.getDueDate() != null) task.setDueDate(req.getDueDate());
        if (req.getAssigneeId() != null) {
            task.setAssignee(resolveAssignee(req.getAssigneeId()));
        }

        taskRepo.save(task);
        return mapToResponse(task);
    }

    @Transactional
    public void deleteTask(UUID taskId, User currentUser) {
        Task task = taskRepo.findByIdWithRelations(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("not found"));

        boolean ownsProject = task.getProject().getOwner().getId().equals(currentUser.getId());
        boolean createdTask = task.getCreatedBy() != null
                && task.getCreatedBy().getId().equals(currentUser.getId());

        if (!ownsProject && !createdTask) {
            throw new ForbiddenActionException("you can only delete tasks you created or tasks in projects you own");
        }

        taskRepo.delete(task);
    }

    static TaskResponse mapToResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .projectId(task.getProject().getId())
                .assigneeId(task.getAssignee() != null ? task.getAssignee().getId() : null)
                .assigneeName(task.getAssignee() != null ? task.getAssignee().getName() : null)
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private User resolveAssignee(UUID assigneeId) {
        if (assigneeId == null) return null;
        return userRepo.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("assignee not found"));
    }
}
