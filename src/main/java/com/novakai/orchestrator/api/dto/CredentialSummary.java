package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.enums.CredentialType;

public record CredentialSummary(Long id, String ref, CredentialType type) {}
