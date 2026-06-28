package com.novakai.orchestrator.domain.config;

public record LogCleanupConfig(
    String directory,
    String filePattern
) {}
