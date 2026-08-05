package com.novakai.orchestrator.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrometheusEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplate restTemplate;

    String base() { return "http://localhost:" + port; }

    @Test
    void prometheus_endpoint_returns_200_with_metrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/actuator/prometheus", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Verify standard JVM metrics are present (Micrometer auto-config)
        assertTrue(response.getBody().contains("jvm_memory"), "Should contain JVM memory metrics");
    }
}
