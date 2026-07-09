package com.novakai.orchestrator.domain.config;

// @author Siddhant Choudhary

import java.util.List;

public record LogCleanupConfig(
    String directory,
    String filePattern,
    List<String> extraPatterns
) {}
