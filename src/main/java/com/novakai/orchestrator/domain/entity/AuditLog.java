package com.novakai.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "USERNAME", nullable = false, length = 200)
    private String username;

    @Column(name = "ACTION", nullable = false, length = 200)
    private String action;

    @Column(name = "ENTITY_TYPE", length = 100)
    private String entityType;

    @Column(name = "ENTITY_ID")
    private Long entityId;

    @Column(name = "DETAIL", length = 2000)
    private String detail;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
