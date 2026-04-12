package com.taskflow.backend.controllers;

import com.taskflow.backend.dto.CreateTaskRequest;
import com.taskflow.backend.dto.PaginatedResponse;
import com.taskflow.backend.dto.TaskResponse;
import com.taskflow.backend.dto.UpdateTaskRequest;
import com.taskflow.backend.entities.User;
import com.taskflow.backend.services.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<PaginatedResponse<TaskResponse>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assignee,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(taskService.listTasks(projectId, status, assignee, page, limit));
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(projectId, req, user));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<TaskResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.updateTask(id, req, user));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        taskService.deleteTask(id, user);
        return ResponseEntity.noContent().build();
    }
}
