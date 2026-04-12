package com.taskflow.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateTaskRequest {
    private String title;
    private String description;
    private String status;
    private String priority;
    private UUID assigneeId;
    private LocalDate dueDate;
}
