package com.novakai.orchestrator.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Response DTO for notification subscription. */
public record NotificationSubscriptionResponse(
    Long id,
    Long jobId,
    String channelType,
    String events,
    Map<String, Object> config,
    boolean active,
    LocalDateTime createdAt
) {}
