package com.novakai.orchestrator.api.dto;

/** Summary of a team the current user belongs to */
public record TeamSummary(Long teamId, String teamName, String role) {
}
