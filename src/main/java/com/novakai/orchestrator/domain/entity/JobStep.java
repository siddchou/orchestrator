package com.novakai.orchestrator.domain.entity;

import com.novakai.orchestrator.domain.enums.StepType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "JOB_STEP")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STEP_ID")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false)
    private JobDefinition jobDefinition;

    @Column(name = "STEP_NAME", nullable = false)
    private String stepName;

    @Column(name = "STEP_ORDER", nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STEP_TYPE", nullable = false, length = 50)
    private StepType stepType;

    @Lob
    @Column(name = "STEP_CONFIG")
    private String stepConfig;

    @Column(name = "CONTINUE_ON_FAILURE", nullable = false)
    private String continueOnFailure = "N";

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";
}
