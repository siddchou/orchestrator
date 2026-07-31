package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Import definition for a step dependency. Uses step names which are resolved to DB IDs during import.
 */
@JsonInclude(JsonInclude.Include.USE_DEFAULTS)
public record ImportDependencyDefinition(
        String stepName,
        String dependsOnStepName,
        String edgeCondition
) {
}
