package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobDefinition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionRepository extends JpaRepository<JobDefinition, Long> {
    @EntityGraph(attributePaths = {"steps"})
    @Query("SELECT j FROM JobDefinition j WHERE j.jobId = :id")
    Optional<JobDefinition> findByIdWithSteps(Long id);

    Optional<JobDefinition> findByJobName(String jobName);

    @EntityGraph(attributePaths = {"steps"})
    @Query("SELECT j FROM JobDefinition j WHERE j.jobName = :name")
    Optional<JobDefinition> findByJobNameWithSteps(String name);

    List<JobDefinition> findByEnabledOrderByJobNameAsc(String enabled);
    Page<JobDefinition> findByJobNameContainingIgnoreCase(String jobName, Pageable pageable);
    Page<JobDefinition> findAll(Pageable pageable);
}
