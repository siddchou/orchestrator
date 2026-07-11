package com.novakai.orchestrator.domain.config;

// @author Siddhant Choudhary

public record SftpConfig(
    String host,
    int port,
    String username,
    String credentialRef,
    String remoteDir,
    String filePattern,
    String direction,
    String remoteFileName,
    Integer connectionTimeoutSeconds,
    Integer authTimeoutSeconds
) {}
