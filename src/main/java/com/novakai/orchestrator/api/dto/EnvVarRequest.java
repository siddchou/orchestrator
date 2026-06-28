package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EnvVarRequest(
    @NotBlank String key,
    @NotBlank String value,
    boolean isGlobal
) {}
