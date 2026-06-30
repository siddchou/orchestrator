package com.novakai.orchestrator.engine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialDecryptionServiceTest {

    private CredentialDecryptionService service;

    @BeforeEach
    void setUp() {
        service = new CredentialDecryptionService();
        // Set the encryption key via reflection-like approach
        // Since @Value won't work in unit test, we use a public setter approach
        // Actually, let's just test with the default key
        try {
            java.lang.reflect.Field field = service.getClass().getDeclaredField("encryptionKey");
            field.setAccessible(true);
            field.set(service, "test-encryption-key-32-bytes-xx!");
        } catch (Exception e) {
            fail("Failed to set encryption key: " + e.getMessage());
        }
        service.init();
    }

    @Test
    void encrypt_then_decrypt_returns_original() throws Exception {
        String plainText = "my-secret-password";

        String encrypted = service.encrypt(plainText);
        String decrypted = service.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    void encrypt_produces_different_ciphertext_each_time() throws Exception {
        String plainText = "same-text";

        String encrypted1 = service.encrypt(plainText);
        String encrypted2 = service.encrypt(plainText);

        // GCM mode uses random IV, so ciphertexts should differ
        assertNotEquals(encrypted1, encrypted2);

        // But both should decrypt to the same value
        assertEquals(plainText, service.decrypt(encrypted1));
        assertEquals(plainText, service.decrypt(encrypted2));
    }

    @Test
    void decrypt_invalid_base64_throws() {
        assertThrows(Exception.class, () -> service.decrypt("not-valid-base64!!!"));
    }

    @Test
    void decrypt_wrong_key_throws() throws Exception {
        String encrypted = service.encrypt("secret");

        // Create a service with a different key
        CredentialDecryptionService other = new CredentialDecryptionService();
        java.lang.reflect.Field field = other.getClass().getDeclaredField("encryptionKey");
        field.setAccessible(true);
        field.set(other, "different-encryption-key-32bytes!");
        other.init();

        assertThrows(Exception.class, () -> other.decrypt(encrypted));
    }

    @Test
    void encrypt_decrypt_empty_string() throws Exception {
        String encrypted = service.encrypt("");
        String decrypted = service.decrypt(encrypted);

        assertEquals("", decrypted);
    }

    @Test
    void encrypt_decrypt_multiline_text() throws Exception {
        String multiline = "line1\nline2\nline3";

        String encrypted = service.encrypt(multiline);
        String decrypted = service.decrypt(encrypted);

        assertEquals(multiline, decrypted);
    }

    @Test
    void init_with_short_key_pads_to_32() throws Exception {
        CredentialDecryptionService shortKeyService = new CredentialDecryptionService();
        java.lang.reflect.Field field = shortKeyService.getClass().getDeclaredField("encryptionKey");
        field.setAccessible(true);
        field.set(shortKeyService, "short");
        shortKeyService.init();

        // Should not throw
        String encrypted = shortKeyService.encrypt("test");
        String decrypted = shortKeyService.decrypt(encrypted);
        assertEquals("test", decrypted);
    }
}
