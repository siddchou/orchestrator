package com.novakai.orchestrator.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackWebhookChannelTest {

    @Mock
    private RestTemplate restTemplate;

    private SlackWebhookChannel channel;

    void setUpChannel() {
        channel = new SlackWebhookChannel();
        ReflectionTestUtils.setField(channel, "restTemplate", restTemplate);
    }

    private NotificationEvent createEvent(RunStatus status) {
        return new NotificationEvent(
                100L, 5L, "Test Job", status,
                LocalDateTime.of(2026, 7, 31, 12, 0), "system"
        );
    }

    @Test
    void getType_returns_SLACK_WEBHOOK() {
        setUpChannel();
        assertEquals("SLACK_WEBHOOK", channel.getType());
    }

    @Test
    void send_throws_when_webhookUrl_missing() {
        setUpChannel();
        ChannelConfig config = new ChannelConfig(Map.of());

        NotificationException ex = assertThrows(NotificationException.class,
                () -> channel.send(createEvent(RunStatus.SUCCESS), config));
        assertTrue(ex.getMessage().contains("webhookUrl"));
    }

    @Test
    void send_posts_Block_Kit_payload_with_correct_structure() throws Exception {
        setUpChannel();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK));

        NotificationEvent event = createEvent(RunStatus.SUCCESS);
        ChannelConfig config = new ChannelConfig(Map.of("webhookUrl", "https://hooks.slack.com/test"));

        channel.send(event, config);

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).postForEntity(eq("https://hooks.slack.com/test"), entityCaptor.capture(), eq(String.class));

        String body = entityCaptor.getValue().getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"blocks\""));
        assertTrue(body.contains("Test Job"));
        assertTrue(body.contains(":white_check_mark:"));

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payload = mapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) payload.get("blocks");
        assertEquals(2, blocks.size());
        assertEquals("header", blocks.get(0).get("type"));
    }

    @Test
    void send_throws_on_non_2xx_response() {
        setUpChannel();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.NOT_FOUND));

        NotificationEvent event = createEvent(RunStatus.SUCCESS);
        ChannelConfig config = new ChannelConfig(Map.of("webhookUrl", "https://hooks.slack.com/test"));

        NotificationException ex = assertThrows(NotificationException.class,
                () -> channel.send(event, config));
        assertTrue(ex.getMessage().contains("404") || ex.getMessage().contains("NOT_FOUND"));
    }
}
