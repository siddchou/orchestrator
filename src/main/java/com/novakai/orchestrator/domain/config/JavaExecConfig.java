package com.novakai.orchestrator.domain.config;

// @author Siddhant Choudhary

import java.util.List;

public record JavaExecConfig(
    String mainClass,
    String jarPath,
    List<String> args,
    List<String> jvmArgs,
    Integer timeoutMinutes
) {}
