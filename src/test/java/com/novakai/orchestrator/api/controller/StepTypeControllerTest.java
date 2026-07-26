package com.novakai.orchestrator.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.NoOpResponseErrorHandler;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StepTypeControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
    }

    String base() { return "http://localhost:" + port; }

    @Test
    void listStepTypes_returns_all_registered_executors() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            base() + "/api/step-types", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("ENV_SETUP"));
        assertTrue(response.getBody().contains("JAVA_EXEC"));
        assertTrue(response.getBody().contains("HTTP_CALL"));
        assertTrue(response.getBody().contains("SHELL_EXEC"));
        assertTrue(response.getBody().contains("DB_QUERY"));
        assertTrue(response.getBody().contains("ARCHIVE"));
        assertTrue(response.getBody().contains("LOG_CLEANUP"));
        assertTrue(response.getBody().contains("SFTP"));
    }

    @Test
    void listStepTypes_returns_json_array() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            base() + "/api/step-types", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.startsWith("[") || body.trim().startsWith("["),
            "Expected JSON array but got: " + body.substring(0, Math.min(50, body.length())));
    }

    @Test
    void listStepTypes_includes_display_names() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            base() + "/api/step-types", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("displayName"));
        assertTrue(response.getBody().contains("fields"));
    }
}
