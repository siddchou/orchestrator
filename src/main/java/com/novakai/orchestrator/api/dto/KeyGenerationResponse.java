package com.novakai.orchestrator.api.dto;

public record KeyGenerationResponse(
        String privateKey,
        String publicKey,
        String fingerprint,
        String algorithm
) {}
