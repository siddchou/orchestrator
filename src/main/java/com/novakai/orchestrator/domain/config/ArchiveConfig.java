package com.novakai.orchestrator.domain.config;

import java.util.List;

public record ArchiveConfig(
    String sourceDir,
    List<String> filePatterns,
    String archiveDir,
    String archiveFormat
) {}
