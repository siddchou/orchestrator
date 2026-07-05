package com.novakai.orchestrator.domain.entity;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "JOB_RUN")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RUN_ID")
    private Long runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false)
    private JobDefinition jobDefinition;

    @Column(name = "TRIGGERED_BY")
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRIGGER_TYPE", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "jobRun", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<JobRunStep> runSteps = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
