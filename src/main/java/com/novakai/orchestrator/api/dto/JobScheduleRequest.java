package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;

public record JobScheduleRequest(
    @NotBlank String cronExpression
) {}