package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.enums.StepType;
import java.util.List;

public record JobStepResponse(
    Long stepId,
    String stepName,
    Integer stepOrder,
    StepType stepType,
    String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}
