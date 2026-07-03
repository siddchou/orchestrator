package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobRunStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobRunStepRepository extends JpaRepository<JobRunStep, Long> {
    List<JobRunStep> findByJobRun_RunIdOrderByStepOrderAsc(Long runId);

    @Query("SELECT s FROM JobRunStep s WHERE s.jobRun.runId = :runId AND s.status NOT IN ('SUCCESS', 'FAILED')")
    List<JobRunStep> findIncompleteStepsByRunId(@org.springframework.data.repository.query.Param("runId") Long runId);
}
