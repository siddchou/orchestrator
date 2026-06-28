package com.novakai.orchestrator.domain.entity;

import com.novakai.orchestrator.domain.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "JOB_RUN_STEP")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRunStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RUN_STEP_ID")
    private Long runStepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RUN_ID", nullable = false)
    private JobRun jobRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private JobStep jobStep;

    @Column(name = "STEP_ORDER")
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RunStatus status = RunStatus.PENDING;

    @Lob
    @Column(name = "LOG_OUTPUT")
    private String logOutput;

    @Column(name = "EXIT_CODE")
    private Integer exitCode;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;
}
