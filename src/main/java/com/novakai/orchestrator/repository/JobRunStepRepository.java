package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobRunStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobRunStepRepository extends JpaRepository<JobRunStep, Long> {
    List<JobRunStep> findByJobRun_RunIdOrderByStepOrderAsc(Long runId);

    @Query("SELECT s FROM JobRunStep s WHERE s.jobRun.runId = :runId AND s.status NOT IN ('SUCCESS', 'FAILED')")
    List<JobRunStep> findIncompleteStepsByRunId(@org.springframework.data.repository.query.Param("runId") Long runId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM JobRunStep s WHERE s.runStepId = :stepId AND s.jobRun.runId = :runId")
    boolean existsByRunStepIdAndJobRun_RunId(@org.springframework.data.repository.query.Param("stepId") Long stepId, @org.springframework.data.repository.query.Param("runId") Long runId);
}
