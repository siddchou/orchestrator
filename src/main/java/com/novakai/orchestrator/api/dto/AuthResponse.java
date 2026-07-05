package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

public record AuthResponse(String accessToken, String role, boolean passwordExpired) {}
