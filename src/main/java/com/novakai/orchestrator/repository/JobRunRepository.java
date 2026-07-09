package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    Page<JobRun> findByJobDefinition_JobId(Long jobId, Pageable pageable);
    Page<JobRun> findByStatus(RunStatus status, Pageable pageable);
    List<JobRun> findAllByStatus(RunStatus status);
    boolean existsByJobDefinition_JobIdAndStatus(Long jobId, RunStatus status);

    @Query("SELECT r FROM JobRun r WHERE " +
           "(:jobId IS NULL OR r.jobDefinition.jobId = :jobId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:from IS NULL OR r.createdAt >= :from) AND " +
           "(:to IS NULL OR r.createdAt <= :to)")
    Page<JobRun> filterRuns(@Param("jobId") Long jobId,
                           @Param("status") RunStatus status,
                           @Param("from") java.time.LocalDateTime from,
                           @Param("to") java.time.LocalDateTime to,
                           Pageable pageable);
}