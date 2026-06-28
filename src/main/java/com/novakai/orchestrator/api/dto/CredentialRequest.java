package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CredentialRequest(
        @NotBlank String ref,
        @NotBlank String type,
        @NotBlank String value
) {}
