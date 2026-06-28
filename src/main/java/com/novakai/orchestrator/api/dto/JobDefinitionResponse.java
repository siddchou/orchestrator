package com.novakai.orchestrator.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobDefinitionResponse(
    Long jobId,
    String jobName,
    String description,
    String workingDir,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<JobStepResponse> steps,
    List<EnvVarResponse> envVars,
    JobScheduleResponse schedule
) {}
