package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export representation of a job schedule.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportSchedule(
        String cronExpression,
        boolean enabled
) {
}
