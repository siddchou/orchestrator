package com.novakai.orchestrator.domain.entity;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.StepType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "JOB_DEFINITION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "JOB_ID")
    private Long jobId;

    @Column(name = "JOB_NAME", nullable = false, unique = true)
    private String jobName;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "WORKING_DIR", nullable = false)
    private String workingDir;

    @Column(name = "JAVA_HOME")
    private String javaHome;

    @Lob
    @Column(name = "CLASSPATH")
    private String classpath;

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<JobStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JobEnvVar> envVars = new ArrayList<>();

    @OneToOne(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
              orphanRemoval = true, fetch = FetchType.LAZY)
    private JobSchedule schedule;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
