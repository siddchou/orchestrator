package com.novakai.orchestrator.domain.config;

public record SftpConfig(
    String host,
    int port,
    String username,
    String credentialRef,
    String remoteDir,
    String filePattern,
    String direction,
    Integer connectionTimeoutSeconds,
    Integer authTimeoutSeconds
) {}
