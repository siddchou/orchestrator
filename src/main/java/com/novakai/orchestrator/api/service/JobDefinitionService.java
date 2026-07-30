package com.novakai.orchestrator.api.service;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.api.mapper.JobDefinitionMapper;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobEnvVar;
import com.novakai.orchestrator.domain.entity.JobSchedule;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.entity.JobStepDependency;
import com.novakai.orchestrator.domain.entity.Team;
import com.novakai.orchestrator.engine.JobSchedulerService;
import com.novakai.orchestrator.engine.exception.CircularDependencyException;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import com.novakai.orchestrator.repository.JobScheduleRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import com.novakai.orchestrator.repository.TeamRepository;
import com.novakai.orchestrator.security.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobDefinitionService {

    private final JobDefinitionRepository jobRepo;
    private final JobStepRepository stepRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobScheduleRepository scheduleRepo;
    private final JobStepDependencyRepository stepDepRepo;
    private final TeamRepository teamRepo;
    private final JobDefinitionMapper mapper;
    private final JobSchedulerService schedulerService;

    @Transactional(readOnly = true)
    public Page<JobDefinitionResponse> listJobs(String search, Pageable pageable, Long teamId) {
        Pageable sorted = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by("jobName").ascending()
        );
        Page<JobDefinition> result;

        if (teamId != null) {
            // Team-scoped query
            if (StringUtils.hasText(search)) {
                result = jobRepo.findByJobNameContainingIgnoreCaseAndTeamId(search, teamId, sorted);
            } else {
                result = jobRepo.findByTeamId(teamId, sorted);
            }
        } else {
            // No team filter — ADMIN or backward compat mode
            if (StringUtils.hasText(search)) {
                result = jobRepo.findByJobNameContainingIgnoreCase(search, sorted);
            } else {
                result = jobRepo.findAll(sorted);
            }
        }
        return result.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<JobDefinitionResponse> listJobs(String search, Pageable pageable) {
        return listJobs(search, pageable, null);
    }

    @Transactional
    @Auditable(action = "CREATE_JOB", entityType = "JOB")
    public JobDefinitionResponse createJob(JobDefinitionRequest request, String username, Long teamId) {
        if (jobRepo.findByJobName(request.jobName()).isPresent()) {
            throw new IllegalArgumentException("Job name already exists: " + request.jobName());
        }
        Team team = teamRepo.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));

        JobDefinition job = new JobDefinition();
        mapper.toEntity(request, job);
        job.setEnabled("Y");
        job.setTeam(team);
        job = jobRepo.save(job);
        log.info("Created job '{}' (id={}) for user {} in team {}", job.getJobName(), job.getJobId(), username, teamId);
        return mapper.toResponse(job);
    }

    @Transactional
    public JobDefinitionResponse updateJob(Long jobId, JobDefinitionRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        // Allow rename only if name is not taken by a different job
        jobRepo.findByJobName(request.jobName()).ifPresent(existing -> {
            if (!existing.getJobId().equals(jobId)) {
                throw new IllegalArgumentException("Job name already exists: " + request.jobName());
            }
        });

        mapper.toEntity(request, job);
        job.setUpdatedAt(LocalDateTime.now());
        job = jobRepo.save(job);
        return mapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobDefinitionResponse getJob(Long jobId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return mapper.toResponse(job);
    }

    @Transactional
    @Auditable(action = "DELETE_JOB", entityType = "JOB")
    public void deleteJob(Long jobId) {
        if (!jobRepo.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        jobRepo.deleteById(jobId);
        log.info("Deleted job id={}", jobId);
    }

    @Transactional
    public JobDefinitionResponse toggleEnabled(Long jobId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        job.setEnabled("Y".equals(job.getEnabled()) ? "N" : "Y");
        job = jobRepo.save(job);
        return mapper.toResponse(job);
    }

    // --- Steps ---

    @Transactional
    public JobStepResponse addStep(Long jobId, JobStepRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        // If order clashes, shift existing steps up
        shiftStepsFrom(jobId, request.stepOrder());

        JobStep step = mapper.toStepEntity(request, job);
        step = stepRepo.save(step);
        return mapper.toStepResponse(step);
    }

    @Transactional
    public JobStepResponse updateStep(Long jobId, Long stepId, JobStepRequest request) {
        jobRepo.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
        JobStep step = stepRepo.findById(stepId)
            .filter(s -> s.getJobDefinition().getJobId().equals(jobId))
            .orElseThrow(() -> new JobNotFoundException(stepId));

        step.setStepName(request.stepName());
        step.setStepOrder(request.stepOrder());
        step.setStepType(request.stepType());
        step.setStepConfig(request.stepConfig());
        step.setContinueOnFailure(request.continueOnFailure() ? "Y" : "N");
        step.setEnabled(request.enabled() ? "Y" : "N");
        return mapper.toStepResponse(stepRepo.save(step));
    }

    @Transactional
    public void deleteStep(Long jobId, Long stepId) {
        JobStep step = stepRepo.findById(stepId)
                .filter(s -> s.getJobDefinition().getJobId().equals(jobId))
                .orElseThrow(() -> new JobNotFoundException(stepId));
        stepRepo.delete(step);
        renumberSteps(jobId);
    }

    @Transactional
    public List<JobStepResponse> reorderSteps(Long jobId, List<Long> stepIds) {
        List<JobStep> steps = stepRepo.findByJobDefinition_JobIdOrderByStepOrderAsc(jobId);

        // Validate all provided IDs belong to this job
        Set<Long> ownedIds = steps.stream().map(JobStep::getStepId).collect(Collectors.toSet());
        stepIds.forEach(id -> {
            if (!ownedIds.contains(id)) {
                throw new IllegalArgumentException("Step " + id + " does not belong to job " + jobId);
            }
        });

        // Assign new order based on position in stepIds list
        Map<Long, JobStep> stepMap = steps.stream()
            .collect(Collectors.toMap(JobStep::getStepId, s -> s));
        for (int i = 0; i < stepIds.size(); i++) {
            stepMap.get(stepIds.get(i)).setStepOrder(i + 1);
        }
        return stepRepo.saveAll(steps).stream().map(mapper::toStepResponse).toList();
    }

    // --- Env Vars ---

    @Transactional(readOnly = true)
    public List<EnvVarResponse> listEnvVars(Long jobId) {
        jobRepo.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        return envVarRepo.findByJobDefinition_JobId(jobId)
                .stream().map(mapper::toEnvVarResponse).toList();
    }

    @Transactional
    public EnvVarResponse addEnvVar(Long jobId, EnvVarRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobEnvVar envVar = JobEnvVar.builder()
                .jobDefinition(job)
                .varName(request.key())
                .varValue(request.value())
                .isGlobal("N")
                .build();
        envVar = envVarRepo.save(envVar);
        return mapper.toEnvVarResponse(envVar);
    }

    @Transactional
    public void deleteEnvVar(Long jobId, Long envId) {
        envVarRepo.findById(envId)
            .filter(v -> v.getJobDefinition() != null && v.getJobDefinition().getJobId().equals(jobId))
            .ifPresentOrElse(
                envVarRepo::delete,
                () -> { throw new JobNotFoundException(envId); }
            );
    }

    // --- Schedule ---

    @Transactional(readOnly = true)
    public JobScheduleResponse getSchedule(Long jobId) {
        return scheduleRepo.findByJobDefinition_JobId(jobId)
            .map(mapper::toScheduleResponse)
            .orElse(null);
    }

    @Transactional
    public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
        if (scheduleRepo.findByJobDefinition_JobId(jobId).isPresent()) {
            throw new IllegalStateException("Schedule already exists for job " + jobId
                + ". Use PUT to update.");
        }
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression(request.cronExpression())
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        schedule = scheduleRepo.save(schedule);
        schedulerService.register(schedule);
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public JobScheduleResponse updateSchedule(Long jobId, JobScheduleRequest request) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        schedule.setCronExpression(request.cronExpression());
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        schedulerService.updateSchedule(schedule);
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(Long jobId) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        schedulerService.cancel(schedule.getScheduleId());
        scheduleRepo.delete(schedule);
    }

    @Transactional
    public JobScheduleResponse toggleSchedule(Long jobId, boolean enable) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        schedule.setEnabled(enable ? "Y" : "N");
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        if (enable) {
            schedulerService.register(schedule);
        } else {
            schedulerService.cancel(schedule.getScheduleId());
        }
        return mapper.toScheduleResponse(schedule);
    }

    // --- Dependency CRUD ---

@Transactional(readOnly = true)
    public List<StepDependencyResponse> getDependencies(Long jobId, Long stepId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobStep step = stepRepo.findStepWithJobDefinition(stepId)
                .orElseThrow(() -> new JobNotFoundException("Step " + stepId));
        if (!step.getJobDefinition().getJobId().equals(jobId)) {
            throw new JobNotFoundException("Step " + stepId + " does not belong to job " + jobId);
        }

        return stepDepRepo.findByStep_StepId(stepId).stream()
                .map(dep -> new StepDependencyResponse(
                        dep.getDependencyId(),
                        dep.getDependsOnStep().getStepId(),
                        dep.getDependsOnStep().getStepName(),
                        dep.getEdgeCondition().name()
                ))
                .toList();
    }

    @Transactional
    @Auditable(action = "UPDATE_DEPENDENCIES", entityType = "JOB_STEP")
    public void setDependencies(Long jobId, Long stepId, List<StepDependencyRequest> requests) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobStep step = stepRepo.findStepWithJobDefinition(stepId)
                .orElseThrow(() -> new JobNotFoundException("Step " + stepId));
        if (!step.getJobDefinition().getJobId().equals(jobId)) {
            throw new JobNotFoundException("Step " + stepId + " does not belong to job " + jobId);
        }

        // Validate referenced steps exist and belong to same job
        Set<Long> jobStepIds = job.getSteps().stream()
                .map(JobStep::getStepId)
                .collect(Collectors.toSet());

        for (StepDependencyRequest req : requests) {
            if (req.dependsOnStepId().equals(stepId)) {
                throw new IllegalArgumentException("Step cannot depend on itself");
            }
            if (!jobStepIds.contains(req.dependsOnStepId())) {
                throw new JobNotFoundException("Step " + req.dependsOnStepId() + " not found in job " + jobId);
            }
        }

        // Cycle detection — build graph with proposed edges and run Kahn's algorithm
        validateNoCycle(job, stepId, requests);

        // Replace dependencies: delete old, insert new
        List<JobStepDependency> existingDeps = stepDepRepo.findByStep_StepId(stepId);
        stepDepRepo.deleteAll(existingDeps);

        List<JobStepDependency> newDeps = new ArrayList<>();
        for (StepDependencyRequest req : requests) {
            JobStep parent = stepRepo.findById(req.dependsOnStepId())
                    .orElseThrow(() -> new JobNotFoundException("Step " + req.dependsOnStepId()));
            JobStepDependency.EdgeCondition condition;
            try {
                condition = JobStepDependency.EdgeCondition.valueOf(req.edgeCondition());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid edge condition: " + req.edgeCondition()
                        + ". Must be one of: ON_SUCCESS, ON_FAILURE, ALWAYS");
            }
            newDeps.add(JobStepDependency.builder()
                    .step(step)
                    .dependsOnStep(parent)
                    .edgeCondition(condition)
                    .build());
        }
        stepDepRepo.saveAll(newDeps);
    }

    /** Kahn's algorithm cycle detection on job definition graph with proposed edges. */
    private void validateNoCycle(JobDefinition job, Long targetStepId, List<StepDependencyRequest> newRequests) {
        List<JobStep> steps = job.getSteps();
        Map<Long, JobStep> stepMap = new HashMap<>();
        for (JobStep s : steps) {
            stepMap.put(s.getStepId(), s);
        }

        // Build adjacency: existing deps for all steps EXCEPT target step's old deps
        Map<Long, List<Long>> downstreams = new HashMap<>();
        for (JobStep s : steps) {
            downstreams.put(s.getStepId(), new ArrayList<>());
        }

        // Add all existing dependencies except those belonging to target step
        for (JobStep s : steps) {
            List<JobStepDependency> deps = stepDepRepo.findByStep_StepId(s.getStepId());
            if (s.getStepId().equals(targetStepId)) continue; // skip — will be replaced
            for (JobStepDependency dep : deps) {
                Long parentId = dep.getDependsOnStep().getStepId();
                downstreams.computeIfAbsent(parentId, k -> new ArrayList<>()).add(s.getStepId());
            }
        }

        // Add proposed dependencies
        for (StepDependencyRequest req : newRequests) {
            downstreams.computeIfAbsent(req.dependsOnStepId(), k -> new ArrayList<>()).add(targetStepId);
        }

        // Kahn's algorithm
        Map<Long, Integer> inDegree = new HashMap<>();
        for (JobStep s : steps) {
            inDegree.put(s.getStepId(), 0);
        }
        for (List<Long> targets : downstreams.values()) {
            for (Long t : targets) {
                inDegree.merge(t, 1, Integer::sum);
            }
        }

        List<Long> queue = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            Long node = queue.removeLast();
            visited++;
            for (Long target : downstreams.getOrDefault(node, List.of())) {
                int newDeg = inDegree.get(target) - 1;
                inDegree.put(target, newDeg);
                if (newDeg == 0) queue.add(target);
            }
        }

        if (visited < steps.size()) {
            throw new CircularDependencyException(
                    "Cycle detected: adding these dependencies would create a circular dependency involving "
                            + (steps.size() - visited) + " step(s)");
        }
    }

    // --- Internal helpers ---

    private void shiftStepsFrom(Long jobId, int fromOrder) {
        stepRepo.findByJobDefinition_JobIdOrderByStepOrderAsc(jobId).stream()
            .filter(s -> s.getStepOrder() >= fromOrder)
            .forEach(s -> s.setStepOrder(s.getStepOrder() + 1));
    }

    private void renumberSteps(Long jobId) {
        List<JobStep> steps = stepRepo.findByJobDefinition_JobIdOrderByStepOrderAsc(jobId);
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepOrder(i + 1);
        }
        stepRepo.saveAll(steps);
    }
}