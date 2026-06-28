package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.StepType;
import java.time.LocalDateTime;

public record RunStepDetail(
    Long runStepId,
    String stepName,
    StepType stepType,
    Integer stepOrder,
    RunStatus status,
    Integer exitCode,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
) {}