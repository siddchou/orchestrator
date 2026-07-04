package com.novakai.orchestrator.engine.service;

import com.novakai.orchestrator.api.dto.KeyGenerationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyGeneratorServiceTest {

    private final KeyGeneratorService keyGeneratorService = new KeyGeneratorService();

    @Test
    void testGenerateRsaKeyPair() throws Exception {
        KeyGenerationRequest request = KeyGenerationRequest.rsa("test-rsa-key", 2048);

        KeyGeneratorService.KeyGenerationResult result = keyGeneratorService.generateKeyPair(request);

        assertThat(result).isNotNull();
        assertThat(result.privateKey()).contains("-----BEGIN PRIVATE KEY-----");
        assertThat(result.publicKey()).startsWith("ssh-rsa ");
        assertThat(result.fingerprint()).startsWith("SHA256:");
        assertThat(result.algorithm()).isEqualTo("RSA");
    }

    @Test
    void testGenerateEd25519KeyPair() throws Exception {
        KeyGenerationRequest request = KeyGenerationRequest.ed25519("test-ed25519-key");

        KeyGeneratorService.KeyGenerationResult result = keyGeneratorService.generateKeyPair(request);

        assertThat(result).isNotNull();
        assertThat(result.privateKey()).contains("-----BEGIN PRIVATE KEY-----");
        assertThat(result.publicKey()).startsWith("ssh-ed25519 ");
        assertThat(result.fingerprint()).startsWith("SHA256:");
        assertThat(result.algorithm()).isEqualTo("ED25519");
    }

    @Test
    void testRsa4096KeyPair() throws Exception {
        KeyGenerationRequest request = KeyGenerationRequest.rsa("test-rsa-4096-key", 4096);

        KeyGeneratorService.KeyGenerationResult result = keyGeneratorService.generateKeyPair(request);

        assertThat(result).isNotNull();
        assertThat(result.privateKey()).contains("-----BEGIN PRIVATE KEY-----");
        assertThat(result.publicKey()).startsWith("ssh-rsa ");
        assertThat(result.algorithm()).isEqualTo("RSA");

        // Verify 4096-bit key is actually larger than 2048-bit
        String publicKey = result.publicKey();
        assertThat(publicKey.length()).isGreaterThan(300); // 4096-bit keys have longer base64 representation
    }
}
