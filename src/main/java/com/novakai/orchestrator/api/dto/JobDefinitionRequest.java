package com.novakai.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public record JobDefinitionRequest(
    @NotBlank String jobName,
    String description,
    @NotBlank String workingDir,
    String javaHome,
    List<String> classpathEntries
) {}
