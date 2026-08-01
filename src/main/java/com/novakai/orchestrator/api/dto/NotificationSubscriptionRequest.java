package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Request body for creating/updating a notification subscription. */
public record NotificationSubscriptionRequest(
    @NotNull Long jobId,
    @NotBlank String channelType,
    /** Event names to subscribe to: SUCCESS, FAILED, PARTIAL */
    List<String> events,
    Map<String, Object> config
) {}
