package com.novakai.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "JOB_DEFINITION_VERSION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDefinitionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VERSION_ID")
    private Long versionId;

    @Column(name = "JOB_ID", nullable = false)
    private Long jobId;

    @Column(name = "VERSION_NUMBER", nullable = false)
    private Integer versionNumber;

    @Lob
    @Column(name = "EXPORT_JSON", nullable = false)
    private String exportJson;

    @Column(name = "VERSION_LABEL")
    private String versionLabel;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
