package com.novakai.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "JOB_ENV_VAR")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEnvVar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ENV_ID")
    private Long envId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID")
    private JobDefinition jobDefinition;

    @Column(name = "VAR_NAME", nullable = false)
    private String varName;

    @Column(name = "VAR_VALUE", nullable = false)
    private String varValue;

    @Column(name = "IS_GLOBAL", nullable = false)
    private String isGlobal = "N";
}
