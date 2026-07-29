package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.api.service.JobDefinitionService;
import com.novakai.orchestrator.api.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobDefinitionController {

    private final JobDefinitionService jobService;
    private final TeamService teamService;

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
}