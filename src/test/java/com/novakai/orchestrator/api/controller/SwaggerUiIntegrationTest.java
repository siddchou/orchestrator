package com.novakai.orchestrator.api.controller;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwaggerUiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    void swagger_ui_renders() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/swagger-ui/index.html", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Swagger UI should return 200");
        assertNotNull(response.getBody());
        assertTrue(
                response.getBody().contains("Swagger") || response.getBody().contains("swagger"),
                "Response should contain Swagger UI content");
    }

    @Test
    void api_docs_json_valid() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "API docs should return 200");
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"paths\""), "Response should contain paths key");
        assertTrue(response.getBody().contains("\"openapi\""), "Response should contain openapi version");

        // Verify paths are non-empty by checking for at least one known endpoint prefix
        assertTrue(
                response.getBody().contains("/api/auth") || response.getBody().contains("/api/jobs"),
                "Paths should contain at least auth or jobs endpoints");
    }

    @Test
    void all_controller_prefixes_present() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();

        // Check that major controller path prefixes appear in the spec
        assertTrue(body.contains("/api/auth"), "Should contain /api/auth paths");
        assertTrue(body.contains("/api/jobs"), "Should contain /api/jobs paths");
        assertTrue(body.contains("/api/runs"), "Should contain /api/runs paths");
        assertTrue(body.contains("/api/notifications"), "Should contain /api/notifications paths");
        assertTrue(body.contains("/api/credentials"), "Should contain /api/credentials paths");
        assertTrue(body.contains("/api/audit"), "Should contain /api/audit paths");
        assertTrue(body.contains("/api/teams"), "Should contain /api/teams paths");
        assertTrue(body.contains("/api/step-types"), "Should contain /api/step-types paths");
        assertTrue(body.contains("/api/system"), "Should contain /api/system paths");
    }
}
