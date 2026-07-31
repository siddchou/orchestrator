package com.novakai.orchestrator.engine.template;

import java.util.Map;

/**
 * Context for template resolution: job parameters, completed step results, and environment variables.
 */
public record ResolutionContext(
    Map<String, Object> jobParams,
    Map<String, Map<String, Object>> stepOutputs,
    Map<String, String> envVars
) {}
