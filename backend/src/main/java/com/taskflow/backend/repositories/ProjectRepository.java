package com.taskflow.backend.repositories;

import com.taskflow.backend.entities.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query(value = "SELECT DISTINCT p FROM Project p LEFT JOIN FETCH p.owner LEFT JOIN Task t ON t.project = p " +
           "WHERE p.owner.id = :userId OR t.assignee.id = :userId",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Project p LEFT JOIN Task t ON t.project = p " +
           "WHERE p.owner.id = :userId OR t.assignee.id = :userId")
    Page<Project> findAccessibleByUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.owner WHERE p.id = :id")
    Optional<Project> findByIdWithOwner(@Param("id") UUID id);
}
