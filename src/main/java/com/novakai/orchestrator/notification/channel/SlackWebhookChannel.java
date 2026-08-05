package com.novakai.orchestrator.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationChannel;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class SlackWebhookChannel implements NotificationChannel {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SlackWebhookChannel(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "SLACK_WEBHOOK";
    }

    @Override
    public void send(NotificationEvent event, ChannelConfig config) throws NotificationException {
        String webhookUrl = config.getString("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new NotificationException("Missing required config field: webhookUrl");
        }

        String emoji = switch (event.status().name()) {
            case "SUCCESS" -> ":white_check_mark:";
            case "FAILED" -> ":x:";
            case "PARTIAL" -> ":warning:";
            default -> ":stopwatch:";
        };

        Map<String, Object> payload = Map.of(
            "blocks", List.of(
                Map.of("type", "header", "text", Map.of(
                    "type", "plain_text",
                    "text", emoji + " Job Run: " + event.status()
                )),
                Map.of("type", "section", "fields", List.of(
                    Map.of("type", "mrkdwn", "value", "*Job*\n" + event.jobName()),
                    Map.of("type", "mrkdwn", "value", "*Status*\n" + event.status().name()),
                    Map.of("type", "mrkdwn", "value", "*Run ID*\n" + event.runId()),
                    Map.of("type", "mrkdwn", "value", "*Triggered By*\n" + event.triggeredBy())
                ))
            )
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new NotificationException("Slack returned " + response.getStatusCode());
            }
        } catch (NotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificationException("Failed to post to Slack webhook: " + e.getMessage(), e);
        }
    }

    @Override
    public ChannelConfigSchema getConfigSchema() {
        return new ChannelConfigSchema("SLACK_WEBHOOK", List.of(
            new FieldDefinition("webhookUrl", "Webhook URL", FieldType.STRING, true, null, null,
                "Slack Incoming Webhook URL (https://hooks.slack.com/services/...)"),
            new FieldDefinition("channel", "Channel Override", FieldType.STRING, false, null, null,
                "Override the default channel (optional)")
        ));
    }
}
