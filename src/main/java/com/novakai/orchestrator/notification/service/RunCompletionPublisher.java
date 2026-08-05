package com.novakai.orchestrator.notification.service;

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.notification.event.JobRunCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Publishes JobRunCompletedEvent to Spring's ApplicationEventMulticaster.
 * Called from DagExecutionEngine.finalizeRun() after status is persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunCompletionPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(Long runId, Long jobId, String jobName, RunStatus status,
                        LocalDateTime completedAt, String triggeredBy) {
        log.info("Publishing notification event: run={} job={} status={}", runId, jobName, status);
        JobRunCompletedEvent event = new JobRunCompletedEvent(
            this, runId, jobId, jobName, status, completedAt, triggeredBy);
        eventPublisher.publishEvent(event);
    }
}
