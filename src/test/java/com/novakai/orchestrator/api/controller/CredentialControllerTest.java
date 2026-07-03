package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.CredentialRequest;
import com.novakai.orchestrator.domain.entity.JobCredential;
import com.novakai.orchestrator.domain.enums.CredentialType;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CredentialControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private JobCredentialRepository credRepo;

    @Autowired
    private CredentialDecryptionService cryptoService;

    private Long savedCredId;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
            
        credRepo.deleteAll();
        String encrypted = cryptoService.encrypt("test-password");
        JobCredential cred = JobCredential.builder()
                .credentialRef("test-ref")
                .credType(CredentialType.PASSWORD)
                .credValue(encrypted)
                .createdAt(LocalDateTime.now())
                .build();
        cred = credRepo.save(cred);
        savedCredId = cred.getCredentialId();
    }

    @Test
    void list_credentials() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/credentials", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("test-ref"));
    }

    @Test
    void create_valid_credential() {
        CredentialRequest request = new CredentialRequest("new-ref", "PASSWORD", "secret123");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/credentials", request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("new-ref"));
    }

    @Test
    void create_invalid_type() {
        CredentialRequest request = new CredentialRequest("bad-ref", "INVALID_TYPE", "value");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/credentials", request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("ERROR"));
    }

    @Test
    void create_blank_fields_returns_400() {
        CredentialRequest request = new CredentialRequest("", "", "");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/credentials", request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void delete_credential() {
        restTemplate.delete(base() + "/api/credentials/" + savedCredId);
    }
}
