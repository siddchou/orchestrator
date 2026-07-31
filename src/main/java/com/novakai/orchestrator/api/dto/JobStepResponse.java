package com.novakai.orchestrator.api.dto;

public record JobStepResponse(
    Long stepId,
    String stepName,
    Integer stepOrder,
    String stepType,
    String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}
