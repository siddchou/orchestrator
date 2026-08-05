package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response with JWT token and user role")
public record AuthResponse(
    @Schema(description = "JWT bearer token for API authentication", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
    @Schema(description = "User role", example = "ROLE_ADMIN") String role,
    @Schema(description = "Whether the user must change password on next login") boolean passwordExpired
) {}
