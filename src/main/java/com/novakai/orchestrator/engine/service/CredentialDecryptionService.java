package com.novakai.orchestrator.engine.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
@Slf4j
public class CredentialDecryptionService {

    @Value("${ORCHESTRATOR_ENCRYPTION_KEY:default-encryption-key-32bytes!!}")
    private String encryptionKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.warn("Encryption key is shorter than 32 bytes, padding with zeros");
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
