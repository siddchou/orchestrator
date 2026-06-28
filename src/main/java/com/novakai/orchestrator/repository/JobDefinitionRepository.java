package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionRepository extends JpaRepository<JobDefinition, Long> {
    Optional<JobDefinition> findByJobName(String jobName);
    List<JobDefinition> findByEnabledOrderByJobNameAsc(String enabled);
    Page<JobDefinition> findByJobNameContainingIgnoreCase(String jobName, Pageable pageable);
    Page<JobDefinition> findAll(Pageable pageable);
}
