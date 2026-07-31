package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Import definition for a job step. Mirrors ExportStep but allows nulls for partial updates.
 */
@JsonInclude(JsonInclude.Include.USE_DEFAULTS)
public record ImportStepDefinition(
        String stepName,
        Integer stepOrder,
        String stepType,
        Object stepConfig,
        Boolean continueOnFailure,
        Boolean enabled
) {
}
