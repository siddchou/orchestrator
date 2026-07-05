package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobCredentialRepository extends JpaRepository<JobCredential, Long> {
    Optional<JobCredential> findByCredentialRef(String credentialRef);
}
