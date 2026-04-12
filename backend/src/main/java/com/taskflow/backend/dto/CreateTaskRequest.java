package com.taskflow.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "is required")
    private String title;

    private String description;

    @Pattern(regexp = "low|medium|high", message = "must be low, medium, or high")
    private String priority;

    private UUID assigneeId;
    private LocalDate dueDate;
}
