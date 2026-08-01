package com.novakai.orchestrator.notification;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.repository.NotificationDeliveryLogRepository;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.notification.service.RunCompletionPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 16 integration test — verifies the full notification pipeline:
 * RunCompletionPublisher → JobRunCompletedEvent → NotificationDispatcher → Delivery Log.
 *
 * Uses Spring context with H2 database. The @Async dispatch is exercised by
 * publishing through RunCompletionPublisher (which triggers the event listener).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationDispatcherIntegrationTest {

    @Autowired private RunCompletionPublisher publisher;
    @Autowired private NotificationSubscriptionRepository subscriptionRepo;
    @Autowired private NotificationDeliveryLogRepository deliveryLogRepo;

    @Test
    void fullPipeline_runCompletionCreatesDeliveryLog() throws InterruptedException {
        // 1. Create a notification subscription with an unregistered channel type
        // (Email is conditional on JavaMailSender, which may not be in test profile)
        NotificationSubscription sub = NotificationSubscription.builder()
            .jobId(999L)
            .channelType("INTEGRATION_TEST_CHANNEL")  // intentionally unregistered
            .events("SUCCESS,FAILED")
            .configJson("{}")
            .active(true)
            .build();
        subscriptionRepo.save(sub);
        subscriptionRepo.flush();

        Long subscriptionId = sub.getId();

        // 2. Publish a run completion event (triggers the full async pipeline)
        publisher.publish(
            100L,           // runId
            999L,           // jobId — matches subscription
            "Integration Job",
            RunStatus.SUCCESS,
            "integration_test"
        );

        // 3. Wait for @Async dispatch to complete (notificationExecutor processes it)
        Thread.sleep(2000);

        // 4. Verify a delivery log was created
        List<NotificationDeliveryLog> logs =
            deliveryLogRepo.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);

        assertFalse(logs.isEmpty(), "A delivery log should be created for the subscription");
        NotificationDeliveryLog log = logs.get(0);
        assertEquals(Long.valueOf(100L), log.getRunId());
        assertEquals("INTEGRATION_TEST_CHANNEL", log.getChannelType());
        // The channel is unregistered, so it should be FAILED (CRITICAL-2 behavior)
        assertEquals("FAILED", log.getStatus());
        assertNotNull(log.getErrorMessage());
        assertTrue(log.getErrorMessage().contains("No registered channel for type"));
    }

    @Test
    void publisherWithNoMatchingSubscription_noDeliveryLog() throws InterruptedException {
        // Publish to a job with no subscriptions — should not create any delivery logs
        long countBefore = deliveryLogRepo.count();

        publisher.publish(
            200L,           // runId
            888L,           // jobId — no subscription for this
            "No Sub Job",
            RunStatus.FAILED,
            "integration_test"
        );

        Thread.sleep(1000);

        long countAfter = deliveryLogRepo.count();
        assertEquals(countBefore, countAfter, "No delivery log when no subscription matches");
    }
}
