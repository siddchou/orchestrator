package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import java.time.LocalDateTime;
import java.util.List;

public record JobRunDetail(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    TriggerType triggerType,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds,
    List<RunStepDetail> steps
) {}
