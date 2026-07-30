package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobStepDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobStepDependencyRepository extends JpaRepository<JobStepDependency, Long> {

    /** Find all dependencies where this step is the downstream target (what does this step depend on) */
    List<JobStepDependency> findByStep_StepId(Long stepId);

    /** Find all dependencies where this step is the upstream source (who depends on this step) */
    List<JobStepDependency> findByDependsOnStep_StepId(Long dependsOnStepId);

    /** Delete all dependencies involving steps of a given job (as the target step) */
    @Modifying
    void deleteByStep_JobDefinition_JobId(Long jobId);

    /** Delete all dependencies involving steps of a given job (as the source step) */
    @Modifying
    void deleteByDependsOnStep_JobDefinition_JobId(Long jobId);
}
