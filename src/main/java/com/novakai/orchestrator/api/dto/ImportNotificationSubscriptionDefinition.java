package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Import definition for a notification subscription.
 */
@JsonInclude(JsonInclude.Include.USE_DEFAULTS)
public record ImportNotificationSubscriptionDefinition(
        String channelType,
        List<String> events,
        Map<String, Object> config,
        Boolean active
) {
}
