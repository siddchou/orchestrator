package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import jakarta.validation.constraints.NotBlank;

public record CredentialRequest(
        @NotBlank String ref,
        @NotBlank String type,
        @NotBlank String value
) {}
