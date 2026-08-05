package com.novakai.orchestrator.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationChannel;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GenericWebhookChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "GENERIC_WEBHOOK";
    }

    @Override
    public void send(NotificationEvent event, ChannelConfig config) throws NotificationException {
        String webhookUrl = config.getString("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new NotificationException("Missing required config field: webhookUrl");
        }

        String methodStr = config.getString("method");
        HttpMethod method = methodStr != null ? HttpMethod.valueOf(methodStr.toUpperCase()) : HttpMethod.POST;

        String headersJson = config.getString("headers");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (headersJson != null && !headersJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> headerMap = objectMapper.readValue(headersJson, Map.class);
                headerMap.forEach(headers::set);
            } catch (Exception e) {
                log.warn("Invalid headers JSON in webhook config: {}", e.getMessage());
            }
        }

        Object body = null;
        String payloadTemplate = config.getString("payload");
        if (payloadTemplate != null && !payloadTemplate.isBlank()) {
            String resolved = resolveTemplate(payloadTemplate, event);
            try {
                body = objectMapper.readValue(resolved, Object.class);
            } catch (Exception e) {
                // If template resolves to non-JSON, send as plain string
                body = resolved;
                headers.setContentType(MediaType.TEXT_PLAIN);
            }
        }

        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                webhookUrl, method, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new NotificationException("Webhook returned " + response.getStatusCode());
            }
        } catch (NotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificationException("Failed to call webhook: " + e.getMessage(), e);
        }
    }

    /** Replace {{fieldName}} placeholders with values from the NotificationEvent record. */
    String resolveTemplate(String template, NotificationEvent event) {
        String resolved = template;
        resolved = resolved.replace("{{runId}}", String.valueOf(event.runId()));
        resolved = resolved.replace("{{jobId}}", String.valueOf(event.jobId()));
        resolved = resolved.replace("{{jobName}}", escapeJsonString(event.jobName()));
        resolved = resolved.replace("{{status}}", escapeJsonString(event.status().name()));
        resolved = resolved.replace("{{completedAt}}", escapeJsonString(String.valueOf(event.completedAt())));
        resolved = resolved.replace("{{triggeredBy}}", escapeJsonString(event.triggeredBy() != null ? event.triggeredBy() : ""));

        // Replace any unresolved variables with empty string
        if (resolved.contains("{{")) {
            resolved = resolved.replaceAll("\\{\\{[^}]+\\}\\}", "");
        }
        return resolved;
    }

    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r");
    }

    @Override
    public ChannelConfigSchema getConfigSchema() {
        return new ChannelConfigSchema("GENERIC_WEBHOOK", List.of(
            new FieldDefinition("webhookUrl", "Webhook URL", FieldType.STRING, true, null, null,
                "Full URL for the webhook endpoint (https://...)"),
            new FieldDefinition("method", "HTTP Method", FieldType.ENUM, false, "POST",
                List.of("GET", "POST", "PUT", "PATCH"), "HTTP method for the request"),
            new FieldDefinition("headers", "Headers (JSON)", FieldType.STRING, false, null, null,
                "JSON object of extra HTTP headers, e.g. {\"Authorization\":\"Bearer token\"}"),
            new FieldDefinition("payload", "Payload Template", FieldType.STRING, false, null, null,
                "JSON payload with {{variable}} placeholders: runId, jobId, jobName, status, completedAt, triggeredBy")
        ));
    }
}
