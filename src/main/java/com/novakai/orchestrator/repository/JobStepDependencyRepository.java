package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobStepDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStepDependencyRepository extends JpaRepository<JobStepDependency, Long> {

    /** Find all dependencies where this step is the downstream target (what does this step depend on) */
    List<JobStepDependency> findByStep_StepId(Long stepId);

    /** Find all dependencies where this step is the upstream source (who depends on this step) */
    List<JobStepDependency> findByDependsOnStep_StepId(Long dependsOnStepId);
}
