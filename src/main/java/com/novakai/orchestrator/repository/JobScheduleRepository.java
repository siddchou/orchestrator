package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobScheduleRepository extends JpaRepository<JobSchedule, Long> {
    Optional<JobSchedule> findByJobDefinition_JobId(Long jobId);
    List<JobSchedule> findByEnabled(String enabled);
}
