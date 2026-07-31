package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Import definition for a job environment variable.
 */
@JsonInclude(JsonInclude.Include.USE_DEFAULTS)
public record ImportEnvVarDefinition(
        String key,
        String value,
        Boolean isGlobal
) {
}
