package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import io.swagger.v3.oas.annotations.media.Schema;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Summary of a single job execution run")
public record JobRunSummary(
    @Schema(description = "Unique identifier for this run") Long runId,
    @Schema(description = "ID of the job that was executed") Long jobId,
    @Schema(description = "Name of the job", example = "DailyETL") String jobName,
    @Schema(description = "Final status of the run", example = "SUCCESS") RunStatus status,
    @Schema(description = "How the run was triggered", example = "MANUAL") TriggerType triggerType,
    @Schema(description = "Username who triggered the run") String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    @Schema(description = "Total execution duration in seconds") long durationSeconds
) {}
