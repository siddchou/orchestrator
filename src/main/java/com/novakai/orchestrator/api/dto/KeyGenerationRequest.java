package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record KeyGenerationRequest(
        @NotBlank String ref,
        @NotNull Algorithm algorithm,
        @Positive Integer rsaKeySize
) {
    public enum Algorithm {
        RSA, ED25519
    }

    public static KeyGenerationRequest rsa(String ref, int keySize) {
        return new KeyGenerationRequest(ref, Algorithm.RSA, keySize);
    }

    public static KeyGenerationRequest ed25519(String ref) {
        return new KeyGenerationRequest(ref, Algorithm.ED25519, null);
    }
}
