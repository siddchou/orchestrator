package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Complete job definition with steps, environment variables, and schedule")
public record JobDefinitionResponse(
    @Schema(description = "Unique identifier for the job") Long jobId,
    @Schema(description = "Job name", example = "DailyETL") String jobName,
    @Schema(description = "Human-readable description") String description,
    @Schema(description = "Working directory for execution") String workingDir,
    @Schema(description = "Java home override") String javaHome,
    @Schema(description = "Additional classpath entries") List<String> classpathEntries,
    @Schema(description = "Whether the job is enabled for scheduled execution") boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    @Schema(description = "Ordered list of steps in this job") List<JobStepResponse> steps,
    @Schema(description = "Environment variables scoped to this job") List<EnvVarResponse> envVars,
    @Schema(description = "Cron schedule configuration (null if unscheduled)") JobScheduleResponse schedule
) {}
