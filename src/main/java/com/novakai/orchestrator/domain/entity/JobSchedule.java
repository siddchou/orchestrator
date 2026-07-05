package com.novakai.orchestrator.domain.entity;

// @author Siddhant Choudhary

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "JOB_SCHEDULE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCHEDULE_ID")
    private Long scheduleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false, unique = true)
    private JobDefinition jobDefinition;

    @Column(name = "CRON_EXPRESSION", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";

    @Column(name = "NEXT_FIRE_TIME")
    private LocalDateTime nextFireTime;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

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
