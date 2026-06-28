package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStepRepository extends JpaRepository<JobStep, Long> {
    List<JobStep> findByJobDefinition_JobIdOrderByStepOrderAsc(Long jobId);
}
