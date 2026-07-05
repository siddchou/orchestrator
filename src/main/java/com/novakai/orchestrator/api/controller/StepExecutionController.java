package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.api.service.JobRunQueryService;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.security.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class StepExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    @PostMapping("/steps/name/{stepName}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Auditable(action = "TRIGGER_STEP", entityType = "JOB_RUN")
    public ApiResponse<JobRunSummary> runStepByName(
            @PathVariable String stepName,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        var run = launchService.launchStepByName(stepName, TriggerType.MANUAL, username);
        log.info("Step '{}' triggered by {}", stepName, username);
        return ApiResponse.success(runQueryService.toRunSummary(run));
    }
}
