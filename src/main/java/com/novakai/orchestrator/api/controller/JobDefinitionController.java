package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.api.service.JobDefinitionService;
import com.novakai.orchestrator.api.service.JobExportImportService;
import com.novakai.orchestrator.api.service.JobVersionService;
import com.novakai.orchestrator.api.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobDefinitionController {

    private final JobDefinitionService jobService;
    private final JobExportImportService exportImportService;
    private final JobVersionService versionService;
    private final TeamService teamService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ApiResponse<Page<JobDefinitionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {

        // Global ADMIN bypasses team filter; otherwise use header or auto-resolve
        Long effectiveTeamId = null;
        if (teamId != null && userDetails != null) {
            effectiveTeamId = teamId;
        } else if (userDetails != null && !teamService.isGlobalAdmin(userDetails.getUsername())) {
            // Non-admin without header — auto-resolve from single-team membership
            var teams = teamService.listUserTeams(userDetails.getUsername());
            if (teams.size() == 1) {
                effectiveTeamId = teams.get(0).teamId();
            }
        }

        return ApiResponse.success(jobService.listJobs(search, PageRequest.of(page, size), effectiveTeamId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobDefinitionResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Team-Id", required = false) Long teamId,
            @Valid @RequestBody JobDefinitionRequest request) {

        log.info("POST /api/jobs: userDetails={}, teamId={}",
                userDetails != null ? userDetails.getUsername() : "NULL", teamId);
        String username = userDetails != null ? userDetails.getUsername() : null;
        if (username == null) {
            log.warn("POST /api/jobs: no authenticated user — SecurityContext may be empty");
            throw new IllegalArgumentException("Authentication is required to create a job");
        }

        // Resolve effective team: explicit header > auto-resolve from membership > error
        // Jobs must belong to a team; even ADMINs need one for creation (ADMIN bypasses filtering, not ownership).
        Long effectiveTeamId = teamId;
        if (effectiveTeamId == null) {
            var teams = teamService.listUserTeams(username);
            if (teams.size() == 1) {
                effectiveTeamId = teams.get(0).teamId();
            } else if (!teams.isEmpty()) {
                // Multi-team user with no active team set — pick first as fallback
                effectiveTeamId = teams.get(0).teamId();
                log.warn("POST /api/jobs: no active team, auto-selected {} for {}", effectiveTeamId, username);
            }
        }
        if (effectiveTeamId == null) {
            throw new IllegalArgumentException("No team available — user must belong to at least one team to create a job");
        }

        // Validate membership when an explicit header was provided
        if (teamId != null) {
            teamService.validateMembership(username, teamId);
        }

        return ApiResponse.success(jobService.createJob(request, username, effectiveTeamId));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobDefinitionResponse> get(@PathVariable Long id) {
        return ApiResponse.success(jobService.getJob(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobDefinitionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody JobDefinitionRequest request) {
        return ApiResponse.success(jobService.updateJob(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        jobService.deleteJob(id);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<JobDefinitionResponse> toggleEnabled(@PathVariable Long id) {
        return ApiResponse.success(jobService.toggleEnabled(id));
    }

    // --- Export / Import ---

    @GetMapping("/{id}/export")
    public ResponseEntity<String> exportJob(@PathVariable Long id,
                                            @RequestParam(defaultValue = "json") String format) {
        String content;
        MediaType mediaType;
        String extension;

        if ("yaml".equalsIgnoreCase(format)) {
            content = exportImportService.exportToYaml(id);
            mediaType = MediaType.parseMediaType("text/yaml");
            extension = "yaml";
        } else {
            content = exportImportService.exportToJson(id);
            mediaType = MediaType.APPLICATION_JSON;
            extension = "json";
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"job-export." + extension + "\"")
                .body(content);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobDefinitionResponse> importJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Team-Id", required = false) Long teamId,
            @RequestBody String rawBody) {

        // Parse body — handle both formats:
        // 1. { format: "json", content: "<JSON string>" } (frontend envelope)
        // 2. { formatVersion, mode, jobName, steps[], ... } (structured API payload)
        JobImportRequest request;
        try {
            Map<String, Object> body = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});

            if (body.containsKey("format") && body.containsKey("content") && !body.containsKey("steps")) {
                // Frontend envelope: unwrap { format, content } → parse content as JobImportRequest
                String content = (String) body.get("content");
                request = objectMapper.readValue(content, JobImportRequest.class);
            } else {
                // Structured payload: deserialize directly
                request = objectMapper.readValue(rawBody, JobImportRequest.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid import request body: " + e.getMessage());
        }

        String username = userDetails != null ? userDetails.getUsername() : null;

        // Resolve team (same logic as create)
        Long effectiveTeamId = teamId;
        if (effectiveTeamId == null && username != null) {
            var teams = teamService.listUserTeams(username);
            if (!teams.isEmpty()) {
                effectiveTeamId = teams.get(0).teamId();
            }
        }

        // Validate before executing
        boolean exists = jobService.jobExistsByName(request.jobName());
        List<String> errors = exportImportService.validateImport(request, exists);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Import validation failed: " + String.join("; ", errors));
        }

        // Save version snapshot before UPDATE import
        if (exists && request.modeEnum() == JobImportRequest.Mode.UPDATE) {
            var existingJob = jobService.findJobByName(request.jobName());
            versionService.saveVersion(existingJob.get().getJobId(), username + " (import pre-update)");
        }

        var job = exportImportService.importJob(request, effectiveTeamId);
        return ApiResponse.success(jobService.getJob(job.getJobId()));
    }

    // --- Steps ---

    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobStepResponse> addStep(
            @PathVariable Long id,
            @Valid @RequestBody JobStepRequest request) {
        return ApiResponse.success(jobService.addStep(id, request));
    }

    @PutMapping("/{id}/steps/{stepId}")
    public ApiResponse<JobStepResponse> updateStep(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody JobStepRequest request) {
        return ApiResponse.success(jobService.updateStep(id, stepId, request));
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(@PathVariable Long id, @PathVariable Long stepId) {
        jobService.deleteStep(id, stepId);
    }

    @PutMapping("/{id}/steps/reorder")
    public ApiResponse<List<JobStepResponse>> reorderSteps(
            @PathVariable Long id,
            @Valid @RequestBody StepReorderRequest request) {
        return ApiResponse.success(jobService.reorderSteps(id, request.stepIds()));
    }

    // --- Dependencies ---

    @GetMapping("/{id}/steps/{stepId}/dependencies")
    public ApiResponse<List<StepDependencyResponse>> getDependencies(
            @PathVariable Long id,
            @PathVariable Long stepId) {
        return ApiResponse.success(jobService.getDependencies(id, stepId));
    }

    @PutMapping("/{id}/steps/{stepId}/dependencies")
    public ApiResponse<Void> setDependencies(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @RequestBody List<StepDependencyRequest> requests) {
        jobService.setDependencies(id, stepId, requests);
        return ApiResponse.success();
    }

    // --- Env Vars ---

    @GetMapping("/{id}/env-vars")
    public ApiResponse<List<EnvVarResponse>> listEnvVars(@PathVariable Long id) {
        return ApiResponse.success(jobService.listEnvVars(id));
    }

    @PostMapping("/{id}/env-vars")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnvVarResponse> addEnvVar(
            @PathVariable Long id,
            @Valid @RequestBody EnvVarRequest request) {
        return ApiResponse.success(jobService.addEnvVar(id, request));
    }

    @DeleteMapping("/{id}/env-vars/{envId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnvVar(@PathVariable Long id, @PathVariable Long envId) {
        jobService.deleteEnvVar(id, envId);
    }

    // --- Schedule ---

    @GetMapping("/{id}/schedule")
    public ApiResponse<JobScheduleResponse> getSchedule(@PathVariable Long id) {
        return ApiResponse.success(jobService.getSchedule(id));
    }

    @PostMapping("/{id}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobScheduleResponse> createSchedule(
            @PathVariable Long id,
            @Valid @RequestBody JobScheduleRequest request) {
        return ApiResponse.success(jobService.createSchedule(id, request));
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<JobScheduleResponse> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody JobScheduleRequest request) {
        return ApiResponse.success(jobService.updateSchedule(id, request));
    }

    @DeleteMapping("/{id}/schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable Long id) {
        jobService.deleteSchedule(id);
    }

    @PostMapping("/{id}/schedule/enable")
    public ApiResponse<JobScheduleResponse> enableSchedule(@PathVariable Long id) {
        return ApiResponse.success(jobService.toggleSchedule(id, true));
    }

    @PostMapping("/{id}/schedule/disable")
    public ApiResponse<JobScheduleResponse> disableSchedule(@PathVariable Long id) {
        return ApiResponse.success(jobService.toggleSchedule(id, false));
    }

    // --- Versions ---

    @GetMapping("/{id}/versions")
    public ApiResponse<List<JobVersionSummary>> listVersions(@PathVariable Long id) {
        var versions = versionService.listVersions(id);
        return ApiResponse.success(versions.stream().map(v -> new JobVersionSummary(
                v.getVersionId(),
                v.getVersionNumber(),
                v.getVersionLabel(),
                v.getCreatedAt(),
                v.getCreatedBy()
        )).toList());
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<String> getVersion(@PathVariable Long id, @PathVariable Integer versionNumber) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(versionService.exportVersion(id, versionNumber));
    }

    @PostMapping("/{id}/versions/{versionNumber}/rollback")
    public ApiResponse<JobDefinitionResponse> rollbackToVersion(
            @PathVariable Long id,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : "system";
        versionService.rollbackToVersion(id, versionNumber,
                jobService.getTeamId(id), username);
        return ApiResponse.success(jobService.getJob(id));
    }
}