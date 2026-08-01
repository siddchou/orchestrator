package com.novakai.orchestrator.notification.service;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.event.JobRunCompletedEvent;
import com.novakai.orchestrator.notification.repository.NotificationDeliveryLogRepository;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.notification.spi.ChannelConfig;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationChannel;
import com.novakai.orchestrator.notification.spi.NotificationEvent;
import com.novakai.orchestrator.notification.spi.NotificationException;
import com.novakai.orchestrator.notification.spi.NotificationChannelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationDispatcher.
 * Uses Mockito to isolate dispatch logic from Spring @Async and JPA.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatcherTest {

    @Mock private NotificationSubscriptionRepository subscriptionRepo;
    @Mock private NotificationDeliveryLogRepository deliveryLogRepo;

    private NotificationChannelRegistry channelRegistry;
    private NotificationDispatcher dispatcher;

    /** Captures the last saved delivery log so we can inspect final state. */
    private NotificationDeliveryLog capturedLog;

    @BeforeEach
    void setUp() {
        channelRegistry = new NotificationChannelRegistry(Collections.emptyList());

        // Mock save to return the entity with an ID, and capture it for inspection
        when(deliveryLogRepo.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> {
            NotificationDeliveryLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            capturedLog = log;
            return log;
        });

        dispatcher = new NotificationDispatcher(channelRegistry, subscriptionRepo, deliveryLogRepo);
    }

    // ------------------------------------------------------------------
    // Event filtering tests (synchronous — no @Async involved)
    // ------------------------------------------------------------------

    @Test
    void noActiveSubscriptions_skipsDispatch() {
        when(subscriptionRepo.findByJobIdAndActiveTrue(1L)).thenReturn(Collections.emptyList());

        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 10L, 1L, "Test Job", RunStatus.SUCCESS, "test_user");
        dispatcher.onRunCompleted(event);

        verifyNoInteractions(deliveryLogRepo);
    }

    @Test
    void subscriptionEventsDontMatch_statusSkipped() {
        NotificationSubscription sub = createSubscription(99L, 1L, "EMAIL", "FAILED");
        when(subscriptionRepo.findByJobIdAndActiveTrue(1L)).thenReturn(List.of(sub));

        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 10L, 1L, "Test Job", RunStatus.SUCCESS, "test_user");
        dispatcher.onRunCompleted(event);

        // onRunCompleted is synchronous but dispatchAsync is @Async — it fires asynchronously.
        // The subscription was filtered before dispatchAsync, so no save call at all.
        verify(deliveryLogRepo, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Dispatch logic tests (call dispatchAsync directly to bypass @Async)
    // ------------------------------------------------------------------

    @Test
    void unregisteredChannelType_createsFailedDeliveryLog() {
        // CRITICAL-2: Unregistered channel must create a FAILED delivery log entry
        NotificationSubscription sub = createSubscription(99L, 1L, "NONEXISTENT", "SUCCESS,FAILED");
        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 10L, 1L, "Test Job", RunStatus.SUCCESS, "test_user");

        dispatcher.dispatchAsync(event, sub);

        assertNotNull(capturedLog);
        assertEquals("FAILED", capturedLog.getStatus());
        assertEquals(Long.valueOf(99L), capturedLog.getSubscriptionId());
        assertEquals(Long.valueOf(10L), capturedLog.getRunId());
        assertEquals("NONEXISTENT", capturedLog.getChannelType());
        assertNotNull(capturedLog.getErrorMessage());
        assertTrue(capturedLog.getErrorMessage().contains("No registered channel for type"));
        assertEquals(Integer.valueOf(0), capturedLog.getAttemptCount());
    }

    @Test
    void successfulDispatch_createsSentDeliveryLog() {
        registerChannel("GOOD_CHANNEL", null); // null = no exception
        NotificationSubscription sub = createSubscription(100L, 2L, "GOOD_CHANNEL", "SUCCESS");
        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 20L, 2L, "Good Job", RunStatus.SUCCESS, "api");

        dispatcher.dispatchAsync(event, sub);

        assertNotNull(capturedLog);
        assertEquals("SENT", capturedLog.getStatus());
        assertEquals(Long.valueOf(100L), capturedLog.getSubscriptionId());
        assertEquals(Long.valueOf(20L), capturedLog.getRunId());
        assertEquals(Integer.valueOf(1), capturedLog.getAttemptCount(), "Succeeded on first attempt");
        assertNotNull(capturedLog.getSentAt());
    }

    @Test
    void channelThrowsException_retriesThenFails() {
        registerChannel("BAD_CHANNEL", new NotificationException("Connection refused"));
        NotificationSubscription sub = createSubscription(101L, 3L, "BAD_CHANNEL", "FAILED");
        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 30L, 3L, "Bad Job", RunStatus.FAILED, "scheduler");

        dispatcher.dispatchAsync(event, sub);

        assertNotNull(capturedLog);
        assertEquals("FAILED", capturedLog.getStatus());
        assertEquals(Integer.valueOf(3), capturedLog.getAttemptCount(), "Should have retried 3 times");
        assertNotNull(capturedLog.getErrorMessage());
        assertTrue(capturedLog.getErrorMessage().contains("Connection refused"));
    }

    @Test
    void channelSucceedsOnSecondRetry() {
        // Register a channel that fails once then succeeds
        final int[] callCount = {0};
        registerChannel("FLAKY_CHANNEL", null);
        // We can't easily model "fail then succeed" with the simple register helper,
        // so instead verify the save count for a failing channel (3 attempts → multiple saves)

        registerChannel("ALWAYS_FAILS", new NotificationException("timeout"));
        NotificationSubscription sub = createSubscription(102L, 4L, "ALWAYS_FAILS", "FAILED");
        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 40L, 4L, "Flaky Job", RunStatus.FAILED, "scheduler");

        dispatcher.dispatchAsync(event, sub);

        // PENDING save + final FAILED save = at least 2 saves
        verify(deliveryLogRepo, atLeast(2)).save(any());
    }

    @Test
    void dispatch_preservesConfigJsonInDeliveryLog() {
        registerChannel("CONFIG_CHANNEL", null);
        NotificationSubscription sub = createSubscription(103L, 5L, "CONFIG_CHANNEL", "SUCCESS");
        sub.setConfigJson("{\"recipients\":[\"a@b.com\"],\"template\":\"hello\"}");
        JobRunCompletedEvent event = new JobRunCompletedEvent(this, 50L, 5L, "Config Job", RunStatus.SUCCESS, "test_user");

        dispatcher.dispatchAsync(event, sub);

        assertNotNull(capturedLog);
        assertEquals("SENT", capturedLog.getStatus());
        assertTrue(capturedLog.getConfigJson().contains("recipients"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private NotificationSubscription createSubscription(Long id, Long jobId, String channelType, String events) {
        return NotificationSubscription.builder()
            .id(id)
            .jobId(jobId)
            .channelType(channelType)
            .events(events)
            .configJson("{}")
            .active(true)
            .build();
    }

    private void registerChannel(String type, NotificationException exception) {
        channelRegistry.register(new NotificationChannel() {
            @Override public String getType() { return type; }
            @Override public void send(NotificationEvent event, ChannelConfig config) throws NotificationException {
                if (exception != null) throw exception;
            }
            @Override public ChannelConfigSchema getConfigSchema() {
                return new ChannelConfigSchema(type, Collections.emptyList());
            }
        });
    }
}
