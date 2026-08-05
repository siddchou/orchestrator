package com.novakai.orchestrator.api.dto;

import java.time.LocalDateTime;

public record JobVersionSummary(
        Long versionId,
        int versionNumber,
        String versionLabel,
        LocalDateTime createdAt,
        String createdBy
) {}
