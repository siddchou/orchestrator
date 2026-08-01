package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Notification subscription included in a job export payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportNotificationSubscription(
        String channelType,
        List<String> events,
        Map<String, Object> config,
        boolean active
) {
}
