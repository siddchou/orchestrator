package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Request to create or update a job definition")
public record JobDefinitionRequest(
    @NotBlank @Schema(description = "Unique name for the job", example = "DailyETL") String jobName,
    @Schema(description = "Human-readable description of what the job does") String description,
    @NotBlank @Schema(description = "Working directory for job execution", example = "/opt/jobs/daily-etl") String workingDir,
    @Schema(description = "Java home override for this job") String javaHome,
    @Schema(description = "Additional classpath entries for Java execution steps") List<String> classpathEntries
) {}
