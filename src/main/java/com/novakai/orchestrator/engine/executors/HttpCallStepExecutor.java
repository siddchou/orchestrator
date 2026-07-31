package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpCallStepExecutor implements StepExecutor {

    private final JsonParser jsonParser = new JsonParser();

    @Override
    public String getType() {
        return "HTTP_CALL";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition("url", "URL", FieldType.STRING, true, null, null,
            "Target URL for the HTTP request"));
        fields.add(new FieldDefinition("method", "HTTP Method", FieldType.ENUM, false, "GET",
            List.of("GET", "POST", "PUT", "DELETE", "PATCH"),
            "HTTP method to use"));
        fields.add(new FieldDefinition("headers", "Headers", FieldType.STRING, false, null, null,
            "JSON map of header names to values, e.g. {\"Content-Type\":\"application/json\"}"));
        fields.add(new FieldDefinition("body", "Request Body", FieldType.STRING, false, null, null,
            "Request body string (for POST/PUT/PATCH)"));
        fields.add(new FieldDefinition("expectedStatus", "Expected Status Code", FieldType.NUMBER, false, null, null,
            "If set, the response status code must match this value for success"));
        fields.add(new FieldDefinition("timeoutSeconds", "Timeout (seconds)", FieldType.NUMBER, false, 30, null,
            "Request timeout in seconds"));

        return new StepConfigSchema("HTTP_CALL", "HTTP Call", fields);
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long startTime = System.nanoTime();

        if (ctx.getStepConfig() == null || ctx.getStepConfig().isBlank()) {
            return StepResult.failure("step_config is null or empty", Duration.ofNanos(System.nanoTime() - startTime));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = jsonParser.parse(ctx.getStepConfig(), Map.class);

        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            return StepResult.failure("url is required", Duration.ofNanos(System.nanoTime() - startTime));
        }

        String method = "GET";
        if (config.get("method") != null) {
            method = config.get("method").toString().toUpperCase();
        }

        int timeoutSeconds = 30;
        if (config.get("timeoutSeconds") != null) {
            timeoutSeconds = ((Number) config.get("timeoutSeconds")).intValue();
        }

        Integer expectedStatus = null;
        if (config.get("expectedStatus") != null) {
            expectedStatus = ((Number) config.get("expectedStatus")).intValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, String> headers = null;
        if (config.get("headers") != null && !config.get("headers").toString().isBlank()) {
            try {
                headers = jsonParser.parse(config.get("headers").toString(), Map.class);
            } catch (Exception e) {
                return StepResult.failure("Invalid headers JSON: " + e.getMessage(),
                    Duration.ofNanos(System.nanoTime() - startTime));
            }
        }

        String body = null;
        if (config.get("body") != null) {
            body = config.get("body").toString();
        }

        ctx.getLogSink().log("HTTP " + method + " " + url);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));

            // Apply headers
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            // Set method and body
            HttpRequest request;
            if ("GET".equals(method)) {
                request = requestBuilder.GET().build();
            } else if ("DELETE".equals(method)) {
                request = requestBuilder.DELETE().build();
            } else {
                HttpRequest.BodyPublisher bodyPublisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();
                request = requestBuilder.method(method, bodyPublisher).build();
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            ctx.getLogSink().log("Response: " + statusCode);

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("statusCode", statusCode);
            outputs.put("responseBody", response.body());

            // Capture response headers
            Map<String, List<String>> responseHeaders = response.headers().map();
            if (!responseHeaders.isEmpty()) {
                outputs.put("responseHeaders", responseHeaders);
            }

            // Check expected status
            if (expectedStatus != null && statusCode != expectedStatus) {
                return StepResult.failure(
                    "Expected status " + expectedStatus + " but got " + statusCode,
                    Duration.ofNanos(System.nanoTime() - startTime));
            }

            return StepResult.success(outputs, "HTTP " + method + " returned " + statusCode,
                Duration.ofNanos(System.nanoTime() - startTime));

        } catch (IOException e) {
            return StepResult.failure("IO error: " + e.getMessage(),
                Duration.ofNanos(System.nanoTime() - startTime));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failure("Request interrupted",
                Duration.ofNanos(System.nanoTime() - startTime));
        }
    }
}
