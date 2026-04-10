package com.taskflow.backend.repositories;

import com.taskflow.backend.entities.Project;
import com.taskflow.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOwner(User owner);
}