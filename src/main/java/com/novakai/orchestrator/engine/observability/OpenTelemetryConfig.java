package com.novakai.orchestrator.engine.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration. When otel.exporter.otlp.endpoint is not set,
 * the SDK creates a no-op exporter that drops all spans with zero overhead.
 */
@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${otel.service.name:orchestrator}") String serviceName,
            @Value("${otel.exporter.otlp.endpoint:}") String otlpEndpoint) {

        Resource resource = Resource.builder()
                .put("service.name", serviceName)
                .build();

        var tracerBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        // Only configure an exporter if endpoint is provided
        if (!otlpEndpoint.isBlank()) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .build();
            tracerBuilder.addSpanProcessor(
                    io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(exporter).build());
        }

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerBuilder.build())
                .build();
    }
}
