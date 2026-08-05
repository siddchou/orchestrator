package com.novakai.orchestrator.notification.channel;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericWebhookChannelTest {

    @Mock private RestTemplate restTemplate;

    private GenericWebhookChannel channel;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        // Inject mock RestTemplate via reflection (same pattern as SlackWebhookChannelTest)
        channel = new GenericWebhookChannel();
        java.lang.reflect.Field rtField = GenericWebhookChannel.class.getDeclaredField("restTemplate");
        rtField.setAccessible(true);
        rtField.set(channel, restTemplate);
    }

    @Test
    void getType_returnsGenericWebhook() {
        assertEquals("GENERIC_WEBHOOK", channel.getType());
    }

    @Test
    void send_missingWebhookUrl_throwsException() {
        ChannelConfig config = new ChannelConfig(Collections.emptyMap());
        NotificationEvent event = createEvent();

        assertThrows(NotificationException.class, () -> channel.send(event, config));
    }

    @Test
    void send_successfulPost_returns200() throws Exception {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        Map<String, Object> params = Map.of(
            "webhookUrl", "https://example.com/webhook"
        );
        ChannelConfig config = new ChannelConfig(params);
        channel.send(createEvent(), config);

        verify(restTemplate).exchange(
            eq("https://example.com/webhook"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void send_non2xxResponse_throwsNotificationException() throws Exception {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("fail", HttpStatus.INTERNAL_SERVER_ERROR));

        Map<String, Object> params = Map.of(
            "webhookUrl", "https://example.com/webhook"
        );
        ChannelConfig config = new ChannelConfig(params);

        assertThrows(NotificationException.class, () -> channel.send(createEvent(), config));
    }

    @Test
    void send_restClientError_wrapsAsNotificationException() throws Exception {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        Map<String, Object> params = Map.of(
            "webhookUrl", "https://example.com/webhook"
        );
        ChannelConfig config = new ChannelConfig(params);

        NotificationException ex = assertThrows(NotificationException.class, () -> channel.send(createEvent(), config));
        assertTrue(ex.getMessage().contains("Failed to call webhook"));
    }

    @Test
    void send_customHttpMethod_usesConfiguredMethod() throws Exception {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        Map<String, Object> params = Map.of(
            "webhookUrl", "https://example.com/webhook",
            "method", "PUT"
        );
        ChannelConfig config = new ChannelConfig(params);
        channel.send(createEvent(), config);

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void resolveTemplate_replacesAllVariables() {
        String template = "{\"run\":\"{{runId}}\",\"job\":\"{{jobName}}\",\"status\":\"{{status}}\"}";
        NotificationEvent event = new NotificationEvent(
            42L, 7L, "My Job", RunStatus.SUCCESS, LocalDateTime.of(2026, 1, 1, 12, 0), "alice");

        String resolved = channel.resolveTemplate(template, event);

        assertTrue(resolved.contains("\"run\":\"42\""));
        assertTrue(resolved.contains("\"job\":\"My Job\""));
        assertTrue(resolved.contains("\"status\":\"SUCCESS\""));
    }

    @Test
    void resolveTemplate_replacesUnresolvedVariablesWithEmpty() {
        String template = "{{runId}} and {{unknownField}}";
        NotificationEvent event = createEvent();

        String resolved = channel.resolveTemplate(template, event);

        assertTrue(resolved.contains("42")); // runId resolved
        assertFalse(resolved.contains("{{unknownField}}")); // unresolved replaced with empty
        assertFalse(resolved.contains("{{")); // no leftover placeholders
    }

    @Test
    void resolveTemplate_multipleUnresolvedVariables_allReplaced() {
        String template = "{\"known\":\"{{jobName}}\",\"bad1\":\"{{foo}}\",\"bad2\":\"{{bar}}\"}";
        NotificationEvent event = createEvent();

        String resolved = channel.resolveTemplate(template, event);

        assertTrue(resolved.contains("\"known\":\"Test Job\""));
        assertTrue(resolved.contains("\"bad1\":\"\""));
        assertTrue(resolved.contains("\"bad2\":\"\""));
        assertFalse(resolved.contains("{{"));
    }

    @Test
    void getConfigSchema_returnsAllFields() {
        ChannelConfigSchema schema = channel.getConfigSchema();

        assertEquals("GENERIC_WEBHOOK", schema.type());
        assertEquals(4, schema.fields().size(), "Should have webhookUrl, method, headers, payload");
    }

    private NotificationEvent createEvent() {
        return new NotificationEvent(
            42L, 1L, "Test Job", RunStatus.SUCCESS, LocalDateTime.now(), "test_user");
    }
}
