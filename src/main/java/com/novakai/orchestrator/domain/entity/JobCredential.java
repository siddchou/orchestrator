package com.novakai.orchestrator.domain.entity;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.CredentialType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "JOB_CREDENTIAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CREDENTIAL_ID")
    private Long credentialId;

    @Column(name = "CREDENTIAL_REF", nullable = false, unique = true, length = 100)
    private String credentialRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "CRED_TYPE", nullable = false, length = 20)
    private CredentialType credType;

    @Column(name = "CRED_VALUE", nullable = false, length = 4000)
    private String credValue;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
