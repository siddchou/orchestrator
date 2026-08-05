package com.novakai.orchestrator.domain.entity;

import com.novakai.orchestrator.domain.enums.StepType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "JOB_STEP", uniqueConstraints = @UniqueConstraint(columnNames = {"JOB_ID", "STEP_NAME"}))
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

    @Column(name = "STEP_TYPE", nullable = false, length = 50)
    private String stepType;

    @Lob
    @Column(name = "STEP_CONFIG")
    private String stepConfig;

    @Column(name = "CONTINUE_ON_FAILURE", nullable = false)
    private String continueOnFailure = "N";

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";

    // --- Getters (override Lombok @Getter for stepType) ---
    public Long getStepId() { return stepId; }
    public JobDefinition getJobDefinition() { return jobDefinition; }
    public String getStepName() { return stepName; }
    public Integer getStepOrder() { return stepOrder; }

    /** Raw string — used by the registry for dispatch. */
    public String getStepType() { return stepType; }

    /** Best-effort enum view for legacy callers — returns null for unrecognized values. */
    public StepType getStepTypeEnum() {
        try {
            return StepType.valueOf(stepType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getStepConfig() { return stepConfig; }
    public String getContinueOnFailure() { return continueOnFailure; }
    public String getEnabled() { return enabled; }

    // --- Setters (Lombok @Generated for most, custom for stepType) ---
    public void setStepId(Long v) { this.stepId = v; }
    public void setJobDefinition(JobDefinition v) { this.jobDefinition = v; }
    public void setStepName(String v) { this.stepName = v; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }

    /** Legacy-compatible setter — existing code calling setStepType(StepType.JAVA_EXEC) still works. */
    public void setStepType(StepType type) { this.stepType = type.name(); }

    /** Setter for dynamically-registered types not in the enum. */
    public void setStepType(String type) { this.stepType = type; }

    public void setStepConfig(String v) { this.stepConfig = v; }
    public void setContinueOnFailure(String v) { this.continueOnFailure = v; }
    public void setEnabled(String v) { this.enabled = v; }
}
