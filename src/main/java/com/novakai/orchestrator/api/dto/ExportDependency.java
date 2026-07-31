package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export representation of a step dependency. Uses step names instead of DB IDs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportDependency(
        String stepName,
        String dependsOnStepName,
        String edgeCondition
) {
}
