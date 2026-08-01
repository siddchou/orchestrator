package com.novakai.orchestrator.api.dto;

import java.time.LocalDateTime;

/** Response DTO for notification delivery log entry. */
public record NotificationDeliveryLogResponse(
    Long id,
    Long subscriptionId,
    Long runId,
    String channelType,
    String status,
    Integer attemptCount,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime sentAt
) {}
