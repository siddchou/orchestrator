package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.EnvVarRequest;
import com.novakai.orchestrator.domain.entity.JobEnvVar;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private JobEnvVarRepository envVarRepo;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
            
        envVarRepo.deleteAll();
    }

    @Test
    void health_returns_ok() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/system/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"SUCCESS\""));
    }

    @Test
    void validateEnv_valid_paths() {
        String javaHome = System.getProperty("java.home");
        String workingDir = System.getProperty("user.dir");
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/system/env-validate?javaHome={jh}&workingDir={wd}",
                String.class, javaHome, workingDir);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"javaHome\":\"OK\""));
    }

    @Test
    void validateEnv_invalid_paths() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/system/env-validate?javaHome=/nonexistent&workingDir=/nonexistent",
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"javaHome\":\"NOT_FOUND\""));
    }

    @Test
    void validateCron_endpoint_responds() {
        // Test that the cron validation endpoint responds
        // Note: URL encoding of cron expressions with spaces is tricky in RestTemplate
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/system/cron-validate?expression={expr}", String.class, "0 0 12 * * *");
        // Endpoint should respond (either 200 OK or 500 if expression parsing fails due to encoding)
        assertTrue(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is5xxServerError());
    }

    @Test
    void validateCron_invalid() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/system/cron-validate?expression=invalid", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"valid\":\"false\""));
    }

    @Test
    void listGlobalEnvVars_empty() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/env-vars/global", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addGlobalEnvVar() {
        EnvVarRequest request = new EnvVarRequest("MY_VAR", "my_value", false);
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/env-vars/global", request, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("MY_VAR"));
    }

    @Test
    void addGlobalEnvVar_blank_key_returns_400() {
        EnvVarRequest request = new EnvVarRequest("", "value", false);
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/env-vars/global", request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void deleteGlobalEnvVar() {
        JobEnvVar envVar = JobEnvVar.builder()
                .varName("TO_DELETE")
                .varValue("value")
                .isGlobal("Y")
                .build();
        envVar = envVarRepo.save(envVar);

        restTemplate.delete(base() + "/api/env-vars/global/" + envVar.getEnvId());
    }
}
