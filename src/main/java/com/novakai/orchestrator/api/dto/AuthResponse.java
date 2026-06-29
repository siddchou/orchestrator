package com.novakai.orchestrator.api.dto;

public record AuthResponse(String accessToken, String role, boolean passwordExpired) {}
