package com.novakai.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "JOB_STEP_DEPENDENCY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStepDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DEPENDENCY_ID")
    private Long dependencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private JobStep step;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DEPENDS_ON_STEP_ID", nullable = false)
    private JobStep dependsOnStep;

    @Column(name = "EDGE_CONDITION", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EdgeCondition edgeCondition = EdgeCondition.ON_SUCCESS;

    public enum EdgeCondition {
        ON_SUCCESS,
        ON_FAILURE,
        ALWAYS
    }
}
