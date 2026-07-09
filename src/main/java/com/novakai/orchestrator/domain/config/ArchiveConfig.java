package com.novakai.orchestrator.domain.config;

// @author Siddhant Choudhary

import java.util.List;

public record ArchiveConfig(
    String sourceDir,
    List<String> filePatterns,
    String archiveDir,
    String archiveFormat,
    boolean deleteOriginal
) {}
