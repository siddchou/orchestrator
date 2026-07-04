package com.novakai.orchestrator.engine.service;

import com.novakai.orchestrator.api.dto.KeyGenerationRequest;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;

@Service
public class KeyGeneratorService {

    public KeyGenerationResult generateKeyPair(KeyGenerationRequest request) throws GeneralSecurityException, IOException {
        String algorithm = request.algorithm().name();
        KeyPair keyPair;

        if (algorithm.equals("RSA")) {
            keyPair = generateRsaKeyPair(request.rsaKeySize());
        } else if (algorithm.equals("ED25519")) {
            keyPair = generateEd25519KeyPair();
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }

        String privateKeyPem = formatPrivateKeyPEM(keyPair.getPrivate());
        // Use the requested algorithm for SSH key type, not the JVM's internal name
        String publicKeySshFormat = formatPublicKeySSH(keyPair.getPublic(), request.ref(), algorithm);

        String fingerprint = calculateFingerprint(keyPair.getPublic());

        return new KeyGenerationResult(privateKeyPem, publicKeySshFormat, fingerprint, algorithm);
    }

    private KeyPair generateRsaKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    private KeyPair generateEd25519KeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ED25519");
        return generator.generateKeyPair();
    }

    public String formatPrivateKeyPEM(java.security.PrivateKey privateKey) throws IOException {
        byte[] pkcs8Bytes = privateKey.getEncoded();
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        sb.append(wrapBase64(Base64.getEncoder().encodeToString(pkcs8Bytes)));
        sb.append("-----END PRIVATE KEY-----");
        return sb.toString();
    }

    public String formatPublicKeySSH(java.security.PublicKey publicKey, String comment, String requestedAlgorithm) throws IOException {
        byte[] encoded = publicKey.getEncoded();
        // Use the requested algorithm to determine SSH key type
        String keyType = "ssh-rsa";
        if ("ED25519".equalsIgnoreCase(requestedAlgorithm)) {
            keyType = "ssh-ed25519";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(keyType).append(" ");
        sb.append(Base64.getEncoder().encodeToString(encoded));
        if (comment != null && !comment.isEmpty()) {
            sb.append(" ").append(comment);
        }
        return sb.toString();
    }

    private String calculateFingerprint(PublicKey publicKey) throws NoSuchAlgorithmException {
        byte[] encoded = publicKey.getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA256");
        byte[] digest = md.digest(encoded);

        StringBuilder sb = new StringBuilder();
        sb.append("SHA256:");
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02x", digest[i]));
        }
        return sb.toString();
    }

    private String wrapBase64(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 64) {
            if (i + 64 <= base64.length()) {
                sb.append(base64, i, i + 64).append("\n");
            } else {
                sb.append(base64.substring(i)).append("\n");
            }
        }
        return sb.toString();
    }

    public record KeyGenerationResult(
            String privateKey,
            String publicKey,
            String fingerprint,
            String algorithm
    ) {}
}
