package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobRunStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRunStepRepository extends JpaRepository<JobRunStep, Long> {
    List<JobRunStep> findByJobRun_RunIdOrderByStepOrderAsc(Long runId);
}
