package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Export representation of a job environment variable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportEnvVar(
        String key,
        String value,
        boolean isGlobal
) {
}
