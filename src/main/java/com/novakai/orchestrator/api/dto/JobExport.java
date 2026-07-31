package com.novakai.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Export envelope for a complete job definition in YAML/JSON format.
 * Uses stable names instead of internal DB IDs for portability.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobExport(
        String formatVersion,
        String exportedAt,
        String exportedFrom,
        String jobId,
        String jobName,
        String description,
        String workingDir,
        String javaHome,
        List<String> classpathEntries,
        boolean enabled,
        String teamName,
        List<ExportStep> steps,
        List<ExportDependency> dependencies,
        List<ExportEnvVar> envVars,
        ExportSchedule schedule,
        Map<String, Object> metadata
) {
}
