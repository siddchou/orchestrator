package com.novakai.orchestrator.notification.service;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.event.JobRunCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunCompletionPublisherTest {

    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void publish_createsAndPublishesEvent() {
        RunCompletionPublisher publisher = new RunCompletionPublisher(eventPublisher);

        publisher.publish(10L, 1L, "My Job", RunStatus.SUCCESS, "api_user");

        ArgumentCaptor<JobRunCompletedEvent> captor = ArgumentCaptor.forClass(JobRunCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        JobRunCompletedEvent event = captor.getValue();
        assertEquals(Long.valueOf(10L), event.getRunId());
        assertEquals(Long.valueOf(1L), event.getJobId());
        assertEquals("My Job", event.getJobName());
        assertEquals(RunStatus.SUCCESS, event.getStatus());
        assertEquals("api_user", event.getTriggeredBy());
    }

    @Test
    void publish_handlesNullTriggeredBy() {
        RunCompletionPublisher publisher = new RunCompletionPublisher(eventPublisher);

        publisher.publish(20L, 2L, "Scheduled Job", RunStatus.FAILED, null);

        ArgumentCaptor<JobRunCompletedEvent> captor = ArgumentCaptor.forClass(JobRunCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertNull(captor.getValue().getTriggeredBy());
    }

    @Test
    void publish_propagatesAllStatuses() {
        RunCompletionPublisher publisher = new RunCompletionPublisher(eventPublisher);

        for (RunStatus status : RunStatus.values()) {
            publisher.publish(1L, 1L, "Job", status, "test");
        }

        verify(eventPublisher, times(RunStatus.values().length)).publishEvent(any(JobRunCompletedEvent.class));
    }
}
