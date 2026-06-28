package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.JobRunDetail;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.api.service.JobRunQueryService;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.security.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    @PostMapping("/jobs/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> trigger(
            @PathVariable Long id) {
        var run = launchService.launch(id, TriggerType.MANUAL, "api-user");
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
        launchService.cancel(runId);
        return ApiResponse.success(null);
    }
}