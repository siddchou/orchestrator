package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

public record KeyGenerationResponse(
        String privateKey,
        String publicKey,
        String fingerprint,
        String algorithm
) {}
