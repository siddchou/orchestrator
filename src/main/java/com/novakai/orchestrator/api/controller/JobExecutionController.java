package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.JobRunDetail;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.api.service.JobRunQueryService;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.security.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class JobExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    @PostMapping("/jobs/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> trigger(
            @PathVariable Long id,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        var run = launchService.launch(id, TriggerType.MANUAL, username);
        log.info("Job {} triggered by {}", id, username);
        return ApiResponse.success(runQueryService.toRunSummary(run));
    }

    @PostMapping("/jobs/name/{name}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> triggerByName(
            @PathVariable String name,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        var run = launchService.launchByName(name, TriggerType.MANUAL, username);
        log.info("Job '{}' triggered by {}", name, username);
        return ApiResponse.success(runQueryService.toRunSummary(run));
    }

    @GetMapping("/runs")
    public ApiResponse<Page<JobRunSummary>> listRuns(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) RunStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(runQueryService.listRuns(jobId, status, from, to,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<JobRunDetail> getRun(@PathVariable Long runId) {
        return ApiResponse.success(runQueryService.getRunDetail(runId));
    }

    @GetMapping("/runs/{runId}/steps/{stepId}/log")
    public ApiResponse<String> getStepLog(
            @PathVariable Long runId,
            @PathVariable Long stepId) {
        return ApiResponse.success(runQueryService.getStepLog(runId, stepId));
    }

    @PostMapping("/runs/{runId}/cancel")
    @Auditable(action = "CANCEL_RUN", entityType = "JOB_RUN")
    public ApiResponse<Void> cancel(@PathVariable Long runId) {
        log.info("Cancel requested for run {}", runId);
        launchService.cancel(runId);
        return ApiResponse.success(null);
    }
}