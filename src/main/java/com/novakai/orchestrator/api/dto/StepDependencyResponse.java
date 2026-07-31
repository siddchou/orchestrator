package com.novakai.orchestrator.api.dto;

public record StepDependencyResponse(
        Long stepDependencyId,
        Long dependsOnStepId,
        String dependsOnStepName,
        String edgeCondition
) {
}
