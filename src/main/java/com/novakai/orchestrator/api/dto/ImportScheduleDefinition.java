package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Import definition for a job schedule.
 */
@JsonInclude(JsonInclude.Include.USE_DEFAULTS)
public record ImportScheduleDefinition(
        String cronExpression,
        Boolean enabled
) {
}
