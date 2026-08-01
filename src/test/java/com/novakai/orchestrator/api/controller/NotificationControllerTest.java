package com.novakai.orchestrator.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.NotificationSubscriptionRequest;
import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.repository.NotificationDeliveryLogRepository;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private NotificationSubscriptionRepository subscriptionRepo;

    @Autowired
    private NotificationDeliveryLogRepository deliveryLogRepo;

    private Long savedSubscriptionId;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());

        subscriptionRepo.deleteAll();
        deliveryLogRepo.deleteAll();

        NotificationSubscription sub = NotificationSubscription.builder()
                .jobId(1L)
                .channelType("EMAIL")
                .events("SUCCESS,FAILED")
                .configJson("{\"recipients\":\"admin@example.com\"}")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        sub = subscriptionRepo.save(sub);
        savedSubscriptionId = sub.getId();

        NotificationDeliveryLog log = NotificationDeliveryLog.builder()
                .subscriptionId(savedSubscriptionId)
                .runId(100L)
                .channelType("EMAIL")
                .eventsJson("{\"runId\":100,\"status\":\"SUCCESS\"}")
                .configJson("{\"recipients\":\"admin@example.com\"}")
                .status("SENT")
                .attemptCount(1)
                .createdAt(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .build();
        deliveryLogRepo.save(log);
    }

    // ------------------------------------------------------------------
    // Subscriptions CRUD
    // ------------------------------------------------------------------

    @Test
    void listSubscriptions_returnsAll() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/subscriptions", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SUCCESS"));
        assertTrue(response.getBody().contains("admin@example.com"));
    }

    @Test
    void getSubscription_byId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/subscriptions/" + savedSubscriptionId, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("EMAIL"));
        assertTrue(response.getBody().contains(String.valueOf(savedSubscriptionId)));
    }

    @Test
    void getSubscription_unknownId_returnsError() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/subscriptions/99999", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("ERROR"));
        assertTrue(response.getBody().contains("Subscription not found"));
    }

    @Test
    void getSubscriptionsForJob_returnsActiveOnly() {
        // Create an inactive subscription for the same job
        NotificationSubscription inactive = NotificationSubscription.builder()
                .jobId(1L)
                .channelType("SLACK_WEBHOOK")
                .events("FAILED")
                .configJson("{\"webhook_url\":\"https://hooks.slack.com/test\"}")
                .active(false)
                .createdAt(LocalDateTime.now())
                .build();
        subscriptionRepo.save(inactive);

        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/subscriptions/job/1", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("EMAIL"));
        assertFalse(response.getBody().contains("SLACK_WEBHOOK"));
    }

    @Test
    void createSubscription_validRequest() throws JsonProcessingException {
        NotificationSubscriptionRequest request = new NotificationSubscriptionRequest(
                2L, "GENERIC_WEBHOOK", List.of("SUCCESS"),
                Map.of("url", "https://example.com/hook", "method", "POST"));

        HttpEntity<NotificationSubscriptionRequest> entity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/notifications/subscriptions", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("GENERIC_WEBHOOK"));
        assertTrue(response.getBody().contains("example.com/hook"));
    }

    @Test
    void updateSubscription_existingId() throws JsonProcessingException {
        NotificationSubscriptionRequest request = new NotificationSubscriptionRequest(
                1L, "SLACK_WEBHOOK", List.of("FAILED"),
                Map.of("webhook_url", "https://hooks.slack.com/new"));

        HttpEntity<NotificationSubscriptionRequest> entity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/notifications/subscriptions/" + savedSubscriptionId,
                HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SLACK_WEBHOOK"));
    }

    @Test
    void updateSubscription_unknownId_returnsError() throws JsonProcessingException {
        NotificationSubscriptionRequest request = new NotificationSubscriptionRequest(
                1L, "EMAIL", List.of("SUCCESS"), Map.of("recipients", "x@y.com"));

        HttpEntity<NotificationSubscriptionRequest> entity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/notifications/subscriptions/99999",
                HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("ERROR"));
    }

    @Test
    void deleteSubscription_existingId() {
        restTemplate.delete(base() + "/api/notifications/subscriptions/" + savedSubscriptionId);
        assertFalse(subscriptionRepo.existsById(savedSubscriptionId));
    }

    @Test
    void deleteSubscription_unknownId_returnsError() {
        ResponseEntity<String> response = restTemplate.exchange(
                base() + "/api/notifications/subscriptions/99999", HttpMethod.DELETE, null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("ERROR"));
    }

    private HttpResponse<String> patch(String url) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void toggleSubscription_flipsActiveState() throws Exception {
        HttpResponse<String> response = patch(base() + "/api/notifications/subscriptions/" + savedSubscriptionId + "/toggle");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"active\":false"));

        // Toggle back
        response = patch(base() + "/api/notifications/subscriptions/" + savedSubscriptionId + "/toggle");
        assertTrue(response.body().contains("\"active\":true"));
    }

    @Test
    void toggleSubscription_unknownId_returnsError() throws Exception {
        HttpResponse<String> response = patch(base() + "/api/notifications/subscriptions/99999/toggle");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ERROR"));
    }

    // ------------------------------------------------------------------
    // Channel schemas
    // ------------------------------------------------------------------

    @Test
    void listChannelSchemas_returnsRegisteredChannels() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/channels", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SUCCESS"));
    }

    // ------------------------------------------------------------------
    // Delivery log
    // ------------------------------------------------------------------

    @Test
    void getDeliveryLog_allEntries() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/delivery-log", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SENT"));
    }

    @Test
    void getDeliveryLog_filteredByRunId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/delivery-log?runId=100", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SENT"));

        // Non-matching run ID returns empty list
        response = restTemplate.getForEntity(
                base() + "/api/notifications/delivery-log?runId=99999", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"data\":[]"));
    }

    @Test
    void getDeliveryLog_filteredBySubscriptionId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/notifications/delivery-log?subscriptionId=" + savedSubscriptionId, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SENT"));

        // Non-matching subscription ID returns empty list
        response = restTemplate.getForEntity(
                base() + "/api/notifications/delivery-log?subscriptionId=99999", String.class);
        assertTrue(response.getBody().contains("\"data\":[]"));
    }
}
