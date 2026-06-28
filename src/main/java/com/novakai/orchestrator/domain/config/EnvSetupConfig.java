package com.novakai.orchestrator.domain.config;

import java.util.List;
import java.util.Map;

public record EnvSetupConfig(
    String javaHome,
    List<String> classpathEntries,
    Map<String, String> extraEnvVars
) {}
