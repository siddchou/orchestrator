package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobEnvVarRepository extends JpaRepository<JobEnvVar, Long> {
    List<JobEnvVar> findByIsGlobal(String isGlobal);
    List<JobEnvVar> findByJobDefinition_JobId(Long jobId);
}
