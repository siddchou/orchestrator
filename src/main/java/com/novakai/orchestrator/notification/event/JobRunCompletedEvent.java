package com.novakai.orchestrator.notification.event;

import com.novakai.orchestrator.domain.enums.RunStatus;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Spring ApplicationEvent published when a job run completes (SUCCESS, FAILED, PARTIAL, CANCELLED).
 */
public class JobRunCompletedEvent extends ApplicationEvent {

    private final Long runId;
    private final Long jobId;
    private final String jobName;
    private final RunStatus status;
    private final LocalDateTime completedAt;
    private final String triggeredBy;

    public JobRunCompletedEvent(Object source, Long runId, Long jobId, String jobName,
                                 RunStatus status, LocalDateTime completedAt, String triggeredBy) {
        super(source);
        this.runId = runId;
        this.jobId = jobId;
        this.jobName = jobName;
        this.status = status;
        this.completedAt = completedAt;
        this.triggeredBy = triggeredBy;
    }

    public Long getRunId() { return runId; }
    public Long getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public RunStatus getStatus() { return status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getTriggeredBy() { return triggeredBy; }
}
