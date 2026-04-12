package com.taskflow.backend.services;

import com.taskflow.backend.dto.PaginatedResponse;
import com.taskflow.backend.dto.ProjectRequest;
import com.taskflow.backend.dto.ProjectResponse;
import com.taskflow.backend.dto.ProjectStatsResponse;
import com.taskflow.backend.dto.TaskResponse;
import com.taskflow.backend.entities.Project;
import com.taskflow.backend.entities.Task;
import com.taskflow.backend.entities.User;
import com.taskflow.backend.exception.ForbiddenActionException;
import com.taskflow.backend.exception.ResourceNotFoundException;
import com.taskflow.backend.repositories.ProjectRepository;
import com.taskflow.backend.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepo;
    private final TaskRepository taskRepo;

    public PaginatedResponse<ProjectResponse> listProjects(User user, int page, int limit) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        var result = projectRepo.findAccessibleByUser(user.getId(), pageable);

        List<ProjectResponse> items = result.getContent().stream()
                .map(p -> mapToResponse(p, false))
                .toList();

        return PaginatedResponse.<ProjectResponse>builder()
                .data(items)
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public ProjectResponse getById(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        return mapToResponse(project, true);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest req, User owner) {
        Project project = Project.builder()
                .name(req.getName())
                .description(req.getDescription())
                .owner(owner)
                .build();
        projectRepo.save(project);
        return mapToResponse(project, false);
    }

    @Transactional
    public ProjectResponse update(UUID projectId, ProjectRequest req, User currentUser) {
        Project project = findProjectOrThrow(projectId);
        checkOwnership(project, currentUser);

        if (req.getName() != null) project.setName(req.getName());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        projectRepo.save(project);

        return mapToResponse(project, false);
    }

    @Transactional
    public void delete(UUID projectId, User currentUser) {
        Project project = findProjectOrThrow(projectId);
        checkOwnership(project, currentUser);
        projectRepo.delete(project);
    }

    public ProjectStatsResponse getStats(UUID projectId) {
        if (!projectRepo.existsById(projectId)) {
            throw new ResourceNotFoundException("not found");
        }

        List<Task> tasks = taskRepo.findAllByProjectId(projectId);

        Map<String, Long> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> byAssignee = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getAssignee() != null ? t.getAssignee().getName() : "Unassigned",
                        LinkedHashMap::new,
                        Collectors.counting()));

        return ProjectStatsResponse.builder()
                .totalTasks(tasks.size())
                .byStatus(byStatus)
                .byAssignee(byAssignee)
                .build();
    }

    private Project findProjectOrThrow(UUID id) {
        return projectRepo.findByIdWithOwner(id)
                .orElseThrow(() -> new ResourceNotFoundException("not found"));
    }

    private void checkOwnership(Project project, User user) {
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenActionException("only the project owner can do this");
        }
    }

    private ProjectResponse mapToResponse(Project p, boolean withTasks) {
        var builder = ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .ownerId(p.getOwner().getId())
                .createdAt(p.getCreatedAt());

        if (withTasks) {
            List<TaskResponse> taskList = taskRepo.findByProjectWithFilters(p.getId(), null, null)
                    .stream()
                    .map(TaskService::mapToResponse)
                    .toList();
            builder.tasks(taskList);
        }

        return builder.build();
    }
}
