package com.novakai.orchestrator.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.event.JobRunCompletedEvent;
import com.novakai.orchestrator.notification.repository.NotificationDeliveryLogRepository;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.notification.spi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Listens for JobRunCompletedEvent and dispatches notifications to matching subscriptions.
 * Each subscription is dispatched asynchronously on the notificationExecutor thread pool.
 * Retry logic: 3 attempts with exponential backoff (1s, 5s, 25s delays).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] DELAYS_MS = {1_000, 5_000, 25_000};

    private final NotificationChannelRegistry channelRegistry;
    private final NotificationSubscriptionRepository subscriptionRepo;
    private final NotificationDeliveryLogRepository deliveryLogRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener
    public void onRunCompleted(JobRunCompletedEvent event) {
        List<NotificationSubscription> subscriptions =
            subscriptionRepo.findByJobIdAndActiveTrue(event.getJobId());

        if (subscriptions.isEmpty()) {
            log.debug("No active notification subscriptions for job {}", event.getJobId());
            return;
        }

        for (NotificationSubscription sub : subscriptions) {
            List<String> events = sub.getEventsList();
            String statusName = event.getStatus().name();
            if (!events.contains(statusName)) {
                log.debug("Subscription {} does not match status {} for job {}",
                    sub.getId(), statusName, event.getJobId());
                continue;
            }

            dispatchAsync(event, sub);
        }
    }

    @Async("notificationExecutor")
    public void dispatchAsync(JobRunCompletedEvent event, NotificationSubscription subscription) {
        String channelTypeName = subscription.getChannelType();
        NotificationChannel channel = channelRegistry.get(channelTypeName).orElse(null);
        if (channel == null) {
            log.warn("No registered channel for type '{}'; creating FAILED delivery log", channelTypeName);
            NotificationDeliveryLog failedLog = NotificationDeliveryLog.builder()
                .subscriptionId(subscription.getId())
                .runId(event.getRunId())
                .channelType(channelTypeName)
                .eventsJson(serialize(buildNotificationEvent(event)))
                .configJson(subscription.getConfigJson())
                .status("FAILED")
                .attemptCount(0)
                .errorMessage("No registered channel for type: " + channelTypeName)
                .build();
            deliveryLogRepo.save(failedLog);
            return;
        }

        ChannelConfig config = new ChannelConfig(parseConfig(subscription.getConfigJson()));
        NotificationEvent notificationEvent = buildNotificationEvent(event);

        NotificationDeliveryLog deliveryLog = NotificationDeliveryLog.builder()
            .subscriptionId(subscription.getId())
            .runId(event.getRunId())
            .channelType(subscription.getChannelType())
            .eventsJson(serialize(notificationEvent))
            .configJson(subscription.getConfigJson())
            .status("PENDING")
            .attemptCount(0)
            .build();

        deliveryLog = deliveryLogRepo.save(deliveryLog);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                channel.send(notificationEvent, config);
                deliveryLog.setStatus("SENT");
                deliveryLog.setAttemptCount(attempt + 1);
                deliveryLog.setSentAt(LocalDateTime.now());
                deliveryLogRepo.save(deliveryLog);
                log.info("Notification sent to subscription {} (attempt {})", subscription.getId(), attempt + 1);
                return;
            } catch (NotificationException e) {
                deliveryLog.setAttemptCount(attempt + 1);
                if (attempt < MAX_ATTEMPTS - 1) {
                    long delayMs = DELAYS_MS[attempt];
                    log.warn("Notification to subscription {} attempt {} failed: {}. Retrying in {}ms...",
                        subscription.getId(), attempt + 1, e.getMessage(), delayMs);
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    deliveryLog.setStatus("FAILED");
                    deliveryLog.setErrorMessage(truncate(e.getMessage(), 4000));
                    log.error("Notification to subscription {} failed after {} attempts: {}",
                        subscription.getId(), MAX_ATTEMPTS, e.getMessage());
                }
            }
        }

        if (!"SENT".equals(deliveryLog.getStatus())) {
            deliveryLogRepo.save(deliveryLog);
        }
    }

    private NotificationEvent buildNotificationEvent(JobRunCompletedEvent event) {
        return new NotificationEvent(
            event.getRunId(),
            event.getJobId(),
            event.getJobName(),
            event.getStatus(),
            event.getCompletedAt(),
            event.getTriggeredBy()
        );
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyMap();
        try {
            return objectMapper.readValue(json, java.util.Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse subscription config JSON: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
