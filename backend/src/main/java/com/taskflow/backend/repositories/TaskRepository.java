package com.taskflow.backend.repositories;

import com.taskflow.backend.entities.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.assignee LEFT JOIN FETCH t.project LEFT JOIN FETCH t.createdBy " +
           "WHERE t.project.id = :projectId" +
           " AND (:status IS NULL OR t.status = :status)" +
           " AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)" +
           " ORDER BY t.createdAt DESC")
    List<Task> findByProjectWithFilters(
            @Param("projectId") UUID projectId,
            @Param("status") String status,
            @Param("assigneeId") UUID assigneeId);

    @Query(value = "SELECT t FROM Task t LEFT JOIN FETCH t.assignee LEFT JOIN FETCH t.project LEFT JOIN FETCH t.createdBy " +
           "WHERE t.project.id = :projectId" +
           " AND (:status IS NULL OR t.status = :status)" +
           " AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)",
           countQuery = "SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId" +
           " AND (:status IS NULL OR t.status = :status)" +
           " AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)")
    Page<Task> findByProjectWithFiltersPaged(
            @Param("projectId") UUID projectId,
            @Param("status") String status,
            @Param("assigneeId") UUID assigneeId,
            Pageable pageable);

    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.assignee LEFT JOIN FETCH t.project p LEFT JOIN FETCH p.owner LEFT JOIN FETCH t.createdBy WHERE t.id = :id")
    Optional<Task> findByIdWithRelations(@Param("id") UUID id);

    List<Task> findAllByProjectId(UUID projectId);
}
