package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobStepRepository extends JpaRepository<JobStep, Long> {
    List<JobStep> findByJobDefinition_JobIdOrderByStepOrderAsc(Long jobId);

    @Query("SELECT s FROM JobStep s JOIN FETCH s.jobDefinition WHERE s.stepId = :stepId")
    Optional<JobStep> findStepWithJobDefinition(@Param("stepId") Long stepId);
}
