package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.JobDefinitionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionVersionRepository extends JpaRepository<JobDefinitionVersion, Long> {

    List<JobDefinitionVersion> findByJobIdOrderByVersionNumberDesc(Long jobId);

    Optional<JobDefinitionVersion> findByJobIdAndVersionNumber(Long jobId, Integer versionNumber);

    Optional<JobDefinitionVersion> findTopByJobIdOrderByVersionNumberDesc(Long jobId);

    long countByJobId(Long jobId);
}
