package com.novakai.orchestrator.api.controller;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    void operations_have_summaries() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());
        JsonNode paths = root.path("paths");
        assertTrue(paths.isObject(), "paths should be an object");

        int totalOps = 0;
        int withSummary = 0;
        var fieldsIterator = paths.fields();
        while (fieldsIterator.hasNext()) {
            var pathEntry = fieldsIterator.next();
            // Skip actuator endpoints - they're auto-exposed by Spring Boot Actuator
            if (pathEntry.getKey().startsWith("/actuator")) continue;
            JsonNode pathItem = pathEntry.getValue();
            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                JsonNode op = pathItem.path(method);
                if (!op.isMissingNode()) {
                    totalOps++;
                    if (op.has("summary") && !op.get("summary").asText().isBlank()) {
                        withSummary++;
                    } else {
                        fail("Operation " + method.toUpperCase() + " " + pathEntry.getKey() + " missing summary");
                    }
                }
            }
        }
        assertTrue(totalOps > 0, "Should have found operations in the spec");
        assertEquals(totalOps, withSummary, "All operations should have summaries");
    }

    @Test
    void auth_security_scheme_present() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());
        JsonNode schemes = root.path("components").path("securitySchemes");
        assertTrue(schemes.has("bearerAuth"), "Should have bearerAuth security scheme");
        JsonNode bearer = schemes.get("bearerAuth");
        assertEquals("http", bearer.get("type").asText());
        assertEquals("bearer", bearer.get("scheme").asText());
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
