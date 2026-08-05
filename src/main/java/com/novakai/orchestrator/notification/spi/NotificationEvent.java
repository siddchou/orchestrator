package com.novakai.orchestrator.notification.spi;

import com.novakai.orchestrator.domain.enums.RunStatus;

import java.time.LocalDateTime;

public record NotificationEvent(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    LocalDateTime completedAt,
    String triggeredBy
) {}
