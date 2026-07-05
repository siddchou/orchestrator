package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.StepType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record JobStepRequest(
    @NotBlank String stepName,
    @NotNull Integer stepOrder,
    @NotNull StepType stepType,
    @NotNull String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}
