package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobStepRequest(
    @NotBlank String stepName,
    @NotNull Integer stepOrder,
    @NotBlank String stepType,
    @NotNull String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}
