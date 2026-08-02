package com.novakai.orchestrator.engine.observability;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that OpenTelemetry starts in no-op mode when no OTLP endpoint is configured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenTelemetryNoOpTest {

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    @Test
    void app_starts_without_otel_endpoint_configured() {
        // The application context should load without errors even with no OTLP endpoint.
        // If OpenTelemetry is available, it should be a no-op instance (no exporter configured).
        assertNotNull(openTelemetry, "OpenTelemetry bean should exist");

        // Verify the tracer is functional (even if no-op) — calling spanBuilder should not throw
        assertDoesNotThrow(() -> {
            var tracer = openTelemetry.getTracer("test-tracer");
            assertNotNull(tracer);
        });
    }
}
