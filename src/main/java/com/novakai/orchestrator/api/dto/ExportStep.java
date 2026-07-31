package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export representation of a job step. Uses stepName instead of DB ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportStep(
        String stepName,
        int stepOrder,
        String stepType,
        Object stepConfig,
        boolean continueOnFailure,
        boolean enabled
) {
}
