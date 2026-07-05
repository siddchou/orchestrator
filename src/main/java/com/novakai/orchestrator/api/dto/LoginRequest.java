package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
