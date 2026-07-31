package com.novakai.orchestrator.api.dto;

import java.time.LocalDateTime;

public record JobVersionSummary(
        int versionNumber,
        String versionLabel,
        LocalDateTime changedAt,
        String changedBy
) {}
