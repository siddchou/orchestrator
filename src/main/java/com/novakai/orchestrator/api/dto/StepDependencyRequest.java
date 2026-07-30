package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotNull;

public record StepDependencyRequest(
        @NotNull Long dependsOnStepId,
        String edgeCondition
) {
    public StepDependencyRequest {
        if (edgeCondition == null || edgeCondition.isBlank()) {
            edgeCondition = "ON_SUCCESS";
        }
    }
}
