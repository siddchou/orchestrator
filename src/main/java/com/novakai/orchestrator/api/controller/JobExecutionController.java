package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.JobRunDetail;
import com.novakai.orchestrator.api.dto.JobRunRequest;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.api.service.JobRunQueryService;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.security.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Execution", description = "Job runs, step logs, cancel operations")
public class JobExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    @Operation(summary = "Trigger a job run by ID")
    @PostMapping("/jobs/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> trigger(
            @PathVariable Long id,
            @RequestBody(required = false) JobRunRequest request,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        Map<String, Object> params = (request != null && request.getParameters() != null)
            ? request.getParameters() : Map.of();
        var run = launchService.launch(id, TriggerType.MANUAL, username, params);
        log.info("Job {} triggered by {}", id, username);
        return ApiResponse.success(runQueryService.toRunSummary(run));
    }

    @Operation(summary = "Trigger a job run by name")
    @PostMapping("/jobs/name/{name}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> triggerByName(
            @PathVariable String name,
            @RequestBody(required = false) JobRunRequest request,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        Map<String, Object> params = (request != null && request.getParameters() != null)
            ? request.getParameters() : Map.of();
        var run = launchService.launchByName(name, TriggerType.MANUAL, username, params);
        log.info("Job '{}' triggered by {}", name, username);
        return ApiResponse.success(runQueryService.toRunSummary(run));
    }

    @Operation(summary = "List job runs with filters and pagination")
    @GetMapping("/runs")
    public ApiResponse<Page<JobRunSummary>> listRuns(
            @Parameter(description = "Filter by job ID") @RequestParam(required = false) Long jobId,
            @Parameter(description = "Filter by run status: SUCCESS, FAILED, PARTIAL, CANCELLED") @RequestParam(required = false) RunStatus status,
            @Parameter(description = "Start date filter (YYYY-MM-DD)") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "End date filter (YYYY-MM-DD)") @RequestParam(required = false) LocalDate to,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(runQueryService.listRuns(jobId, status, from, to,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Operation(summary = "Get detailed run info with step results")
    @GetMapping("/runs/{runId}")
    public ApiResponse<JobRunDetail> getRun(@PathVariable Long runId) {
        return ApiResponse.success(runQueryService.getRunDetail(runId));
    }

    @Operation(summary = "Get log output for a specific step in a run")
    @GetMapping("/runs/{runId}/steps/{stepId}/log")
    public ApiResponse<String> getStepLog(
            @PathVariable Long runId,
            @PathVariable Long stepId) {
        return ApiResponse.success(runQueryService.getStepLog(runId, stepId));
    }

    @Operation(summary = "Cancel a running job execution")
    @PostMapping("/runs/{runId}/cancel")
    @Auditable(action = "CANCEL_RUN", entityType = "JOB_RUN")
    public ApiResponse<Void> cancel(@PathVariable Long runId) {
        log.info("Cancel requested for run {}", runId);
        launchService.cancel(runId);
        return ApiResponse.success(null);
    }
}
