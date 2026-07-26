package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.enums.RunStatus;
import java.time.LocalDateTime;

public record RunStepDetail(
    Long runStepId,
    String stepName,
    String stepType,
    Integer stepOrder,
    RunStatus status,
    Integer exitCode,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
) {}
