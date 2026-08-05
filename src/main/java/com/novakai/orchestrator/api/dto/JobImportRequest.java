package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Import request envelope. Wraps a job export payload with conflict resolution mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobImportRequest(
        String formatVersion,
        @NotBlank(message = "mode is required: ERROR, UPDATE, or SKIP")
        String mode,
        String jobId,
        String jobName,
        String description,
        String workingDir,
        String javaHome,
        java.util.List<String> classpathEntries,
        Boolean enabled,
        String teamName,
        java.util.List<ImportStepDefinition> steps,
        java.util.List<ImportDependencyDefinition> dependencies,
        java.util.List<ImportEnvVarDefinition> envVars,
        java.util.List<ImportNotificationSubscriptionDefinition> subscriptions,
        ImportScheduleDefinition schedule,
        java.util.Map<String, Object> metadata
) {
    public enum Mode {
        ERROR, UPDATE, SKIP
    }

    /**
     * Returns the conflict resolution mode as an enum. Defaults to ERROR if null/blank.
     */
    public Mode modeEnum() {
        try {
            return Mode.valueOf(mode != null ? mode.toUpperCase() : "ERROR");
        } catch (IllegalArgumentException e) {
            return Mode.ERROR;
        }
    }
}
