package com.novakai.orchestrator.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.JobImportRequest;
import com.novakai.orchestrator.domain.entity.JobDefinitionVersion;
import com.novakai.orchestrator.repository.JobDefinitionVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages job definition version history. Each version stores the full export-format JSON
 * snapshot, enabling rollback and audit trail.
 */
@Service
@Slf4j
public class JobVersionService {

    private final JobDefinitionVersionRepository versionRepo;
    private final JobExportImportService exportImportService;
    private final ObjectMapper mapper;

    public JobVersionService(JobDefinitionVersionRepository versionRepo,
                             JobExportImportService exportImportService) {
        this.versionRepo = versionRepo;
        this.exportImportService = exportImportService;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Create a new version snapshot for a job. Called after any mutating operation.
     * Auto-increments version number per job.
     */
    @Transactional
    public JobDefinitionVersion saveVersion(Long jobId, String changedBy) {
        String exportJson = exportImportService.exportToJson(jobId);

        long currentCount = versionRepo.countByJobId(jobId);
        int nextVersion = (int) currentCount + 1;

        JobDefinitionVersion version = JobDefinitionVersion.builder()
                .jobId(jobId)
                .versionNumber(nextVersion)
                .exportJson(truncateExportJson(exportJson))
                .versionLabel(changedBy)
                .createdBy(changedBy)
                .build();

        version = versionRepo.save(version);
        log.info("Saved version {} for job {}", nextVersion, jobId);
        return version;
    }

    /** List all versions for a job, newest first */
    @Transactional(readOnly = true)
    public List<JobDefinitionVersion> listVersions(Long jobId) {
        return versionRepo.findByJobIdOrderByVersionNumberDesc(jobId);
    }

    /** Get a specific version */
    @Transactional(readOnly = true)
    public JobDefinitionVersion getVersion(Long jobId, Integer versionNumber) {
        return versionRepo.findByJobIdAndVersionNumber(jobId, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + versionNumber + " not found for job " + jobId));
    }

    /** Get the latest version */
    @Transactional(readOnly = true)
    public JobDefinitionVersion getLatestVersion(Long jobId) {
        return versionRepo.findTopByJobIdOrderByVersionNumberDesc(jobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No versions found for job " + jobId));
    }

    /** Export a specific version as JSON string */
    @Transactional(readOnly = true)
    public String exportVersion(Long jobId, Integer versionNumber) {
        JobDefinitionVersion version = getVersion(jobId, versionNumber);
        return version.getExportJson();
    }

    /**
     * Rollback a job to a previous version. Loads the stored export JSON and re-imports it,
     * effectively restoring the job to that point in time. Creates a new version row for the rollback.
     */
    @Transactional
    public JobDefinitionVersion rollbackToVersion(Long jobId, Integer versionNumber, Long teamId, String changedBy) {
        JobDefinitionVersion version = getVersion(jobId, versionNumber);
        String exportJson = version.getExportJson();

        // Parse stored export JSON into import request (extra export fields ignored by Jackson)
        JobImportRequest importRequest;
        try {
            importRequest = mapper.readValue(exportJson, JobImportRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse version " + versionNumber + " JSON", e);
        }
        // Force UPDATE mode since the job exists
        importRequest = new JobImportRequest(
                importRequest.formatVersion(),
                JobImportRequest.Mode.UPDATE.name(),
                importRequest.jobId(),
                importRequest.jobName(),
                importRequest.description(),
                importRequest.workingDir(),
                importRequest.javaHome(),
                importRequest.classpathEntries(),
                importRequest.enabled(),
                importRequest.teamName(),
                importRequest.steps(),
                importRequest.dependencies(),
                importRequest.envVars(),
                importRequest.subscriptions(),
                importRequest.schedule(),
                importRequest.metadata()
        );

        // Validate structurally (cycles, duplicates, cron) but skip step-type check —
        // stored versions may reference step types no longer registered.
        List<String> errors = exportImportService.validateImport(importRequest, /*jobExists=*/true, /*skipStepTypeValidation=*/true);
        if (!errors.isEmpty()) {
            throw new RuntimeException("Version " + versionNumber + " failed structural validation: " + String.join("; ", errors));
        }

        exportImportService.importJob(importRequest, teamId);

        // Create a new version row to record the rollback
        return saveVersion(jobId, changedBy + " (rollback from v" + versionNumber + ")");
    }

    /** Delete all versions for a job (called on job delete) */
    @Transactional
    public void deleteVersionsForJob(Long jobId) {
        List<JobDefinitionVersion> versions = versionRepo.findByJobIdOrderByVersionNumberDesc(jobId);
        versionRepo.deleteAll(versions);
        log.info("Deleted {} versions for job {}", versions.size(), jobId);
    }

    /** Truncate export JSON if it exceeds safe CLOB size */
    private String truncateExportJson(String json) {
        if (json == null) return null;
        int maxChars = 1024 * 1024 / 3; // ~1 MB in UTF-8 chars
        if (json.length() > maxChars) {
            log.warn("Export JSON exceeds {} chars, truncating", maxChars);
            return json.substring(0, maxChars - 3) + "}]}";
        }
        return json;
    }
}
