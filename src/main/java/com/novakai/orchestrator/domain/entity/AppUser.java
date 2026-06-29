package com.novakai.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "APP_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "ROLE", nullable = false, length = 20)
    private String role;

    @Column(name = "ENABLED", nullable = false, length = 1)
    @Builder.Default
    private String enabled = "Y";

    @Column(name = "PASSWORD_EXPIRED", nullable = false, length = 1)
    @Builder.Default
    private String passwordExpired = "N";

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
