package com.taskflow.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class ProjectStatsResponse {
    private long totalTasks;
    private Map<String, Long> byStatus;
    private Map<String, Long> byAssignee;
}
