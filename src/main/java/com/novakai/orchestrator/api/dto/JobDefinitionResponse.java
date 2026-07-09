package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import java.time.LocalDateTime;
import java.util.List;

public record JobDefinitionResponse(
    Long jobId,
    String jobName,
    String description,
    String workingDir,
    String javaHome,
    List<String> classpathEntries,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<JobStepResponse> steps,
    List<EnvVarResponse> envVars,
    JobScheduleResponse schedule
) {}
