package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.CredentialType;

public record CredentialSummary(Long id, String ref, CredentialType type) {}
