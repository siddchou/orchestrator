# Phase 3b — API: Job Definition Controller & Service

> **Goal:** Implement the `JobDefinitionController` and its backing `JobDefinitionService`.
> This covers all CRUD for jobs, steps, environment variables, and schedule management.
> The execution trigger and run history endpoints are in Phase 3c.

> **Depends on:** Phase 3a (DTOs, `DtoMapper`, `ApiResponse`), Phase 1 (entities, repos)  
> **Produces:** `JobDefinitionController`, `JobDefinitionService`

---

## Package Layout for This Sub-Phase

```
com.yourco.orchestrator/
├── api/
│   └── controller/
│       └── JobDefinitionController.java
└── service/
    └── JobDefinitionService.java
```

---

## 3b.1 JobDefinitionService

The service owns all business logic. The controller is thin — it delegates everything here.

```java
// com.yourco.orchestrator.service.JobDefinitionService

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class JobDefinitionService {

    private final JobDefinitionRepository jobRepo;
    private final JobStepRepository stepRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobScheduleRepository scheduleRepo;
    private final DtoMapper mapper;

    // ---------------------------------------------------------------
    // Job CRUD
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<JobDefinitionResponse> listJobs(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return jobRepo
                .findByJobNameContainingIgnoreCase(search, pageable)
                .map(mapper::toResponse);
        }
        return jobRepo.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public JobDefinitionResponse getJob(Long id) {
        return mapper.toResponse(findJob(id));
    }

    public JobDefinitionResponse createJob(JobDefinitionRequest request) {
        if (jobRepo.findByJobName(request.jobName()).isPresent()) {
            throw new IllegalArgumentException("Job name already exists: " + request.jobName());
        }
        JobDefinition job = JobDefinition.builder()
            .jobName(request.jobName())
            .description(request.description())
            .workingDir(request.workingDir())
            .enabled("Y")
            .build();
        return mapper.toResponse(jobRepo.save(job));
    }

    public JobDefinitionResponse updateJob(Long id, JobDefinitionRequest request) {
        JobDefinition job = findJob(id);

        // Allow rename only if name is not taken by a different job
        jobRepo.findByJobName(request.jobName()).ifPresent(existing -> {
            if (!existing.getJobId().equals(id)) {
                throw new IllegalArgumentException("Job name already exists: " + request.jobName());
            }
        });

        job.setJobName(request.jobName());
        job.setDescription(request.description());
        job.setWorkingDir(request.workingDir());
        return mapper.toResponse(jobRepo.save(job));
    }

    public void deleteJob(Long id) {
        JobDefinition job = findJob(id);
        jobRepo.delete(job);
        log.info("Deleted job {}", id);
    }

    public JobDefinitionResponse toggleEnabled(Long id) {
        JobDefinition job = findJob(id);
        job.setEnabled("Y".equals(job.getEnabled()) ? "N" : "Y");
        return mapper.toResponse(jobRepo.save(job));
    }

    // ---------------------------------------------------------------
    // Step management
    // ---------------------------------------------------------------

    public JobStepResponse addStep(Long jobId, JobStepRequest request) {
        JobDefinition job = findJob(jobId);

        // If order clashes, shift existing steps up
        shiftStepsFrom(jobId, request.stepOrder());

        JobStep step = JobStep.builder()
            .jobDefinition(job)
            .stepName(request.stepName())
            .stepOrder(request.stepOrder())
            .stepType(request.stepType())
            .stepConfig(request.stepConfig())
            .continueOnFailure(request.continueOnFailure() ? "Y" : "N")
            .enabled(request.enabled() ? "Y" : "N")
            .build();
        return mapper.toResponse(stepRepo.save(step));
    }

    public JobStepResponse updateStep(Long jobId, Long stepId, JobStepRequest request) {
        findJob(jobId); // validate job exists
        JobStep step = stepRepo.findById(stepId)
            .filter(s -> s.getJobDefinition().getJobId().equals(jobId))
            .orElseThrow(() -> new JobNotFoundException(stepId));

        step.setStepName(request.stepName());
        step.setStepOrder(request.stepOrder());
        step.setStepType(request.stepType());
        step.setStepConfig(request.stepConfig());
        step.setContinueOnFailure(request.continueOnFailure() ? "Y" : "N");
        step.setEnabled(request.enabled() ? "Y" : "N");
        return mapper.toResponse(stepRepo.save(step));
    }

    public void deleteStep(Long jobId, Long stepId) {
        findJob(jobId);
        JobStep step = stepRepo.findById(stepId)
            .filter(s -> s.getJobDefinition().getJobId().equals(jobId))
            .orElseThrow(() -> new JobNotFoundException(stepId));
        stepRepo.delete(step);
        renumberSteps(jobId); // close the gap
    }

    public List<JobStepResponse> reorderSteps(Long jobId, List<Long> stepIds) {
        findJob(jobId);
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
        return stepRepo.saveAll(steps).stream().map(mapper::toResponse).toList();
    }

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

    // ---------------------------------------------------------------
    // Environment variable management
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EnvVarResponse> listEnvVars(Long jobId) {
        findJob(jobId);
        return envVarRepo.findByJobDefinition_JobId(jobId).stream()
            .map(mapper::toResponse).toList();
    }

    public EnvVarResponse addEnvVar(Long jobId, EnvVarRequest request) {
        JobDefinition job = findJob(jobId);
        JobEnvVar var = JobEnvVar.builder()
            .jobDefinition(job)
            .varName(request.varName())
            .varValue(request.varValue())
            .isGlobal("N")
            .build();
        return mapper.toResponse(envVarRepo.save(var));
    }

    public void deleteEnvVar(Long jobId, Long envId) {
        findJob(jobId);
        envVarRepo.findById(envId)
            .filter(v -> v.getJobDefinition() != null
                      && v.getJobDefinition().getJobId().equals(jobId))
            .ifPresentOrElse(
                envVarRepo::delete,
                () -> { throw new JobNotFoundException(envId); }
            );
    }

    // ---------------------------------------------------------------
    // Schedule management
    // Note: schedulerService integration added in Phase 4b
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public JobScheduleResponse getSchedule(Long jobId) {
        return scheduleRepo.findByJobDefinition_JobId(jobId)
            .map(mapper::toResponse)
            .orElse(null);
    }

    public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) {
        findJob(jobId);
        if (scheduleRepo.findByJobDefinition_JobId(jobId).isPresent()) {
            throw new IllegalStateException("Schedule already exists for job " + jobId
                + ". Use PUT to update.");
        }
        JobDefinition job = findJob(jobId);
        JobSchedule schedule = JobSchedule.builder()
            .jobDefinition(job)
            .cronExpression(request.cronExpression())
            .enabled("Y")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        schedule = scheduleRepo.save(schedule);
        // schedulerService.register(schedule) ← wired in Phase 4b
        return mapper.toResponse(schedule);
    }

    public JobScheduleResponse updateSchedule(Long jobId, JobScheduleRequest request) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        schedule.setCronExpression(request.cronExpression());
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        // schedulerService.updateSchedule(schedule) ← wired in Phase 4b
        return mapper.toResponse(schedule);
    }

    public void deleteSchedule(Long jobId) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        // schedulerService.cancel(schedule.getScheduleId()) ← wired in Phase 4b
        scheduleRepo.delete(schedule);
    }

    public JobScheduleResponse toggleSchedule(Long jobId, boolean enable) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
            .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));
        schedule.setEnabled(enable ? "Y" : "N");
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        // schedulerService.register/cancel ← wired in Phase 4b
        return mapper.toResponse(schedule);
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private JobDefinition findJob(Long id) {
        return jobRepo.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }
}
```

---

## 3b.2 JobDefinitionRepository — Additional Method

Add this search method to the existing repository from Phase 1:

```java
// Add to JobDefinitionRepository

Page<JobDefinition> findByJobNameContainingIgnoreCase(String search, Pageable pageable);
```

---

## 3b.3 JobDefinitionController

Thin controller — delegates everything to the service.

```java
// com.yourco.orchestrator.api.controller.JobDefinitionController

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobDefinitionController {

    private final JobDefinitionService jobService;

    // ----- Job CRUD -----

    @GetMapping
    public ApiResponse<Page<JobDefinitionResponse>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String search) {
        return ApiResponse.success(
            jobService.listJobs(search, PageRequest.of(page, size, Sort.by("jobName").ascending()))
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobDefinitionResponse> create(
            @Valid @RequestBody JobDefinitionRequest request) {
        return ApiResponse.success(jobService.createJob(request));
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

    // ----- Steps -----

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

    // ----- Environment Variables -----

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

    // ----- Schedule -----

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
```

---

## 3b.4 Endpoints Produced by This Sub-Phase

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/jobs` | List all jobs (paginated, searchable) |
| POST | `/api/jobs` | Create job |
| GET | `/api/jobs/{id}` | Get job with steps, env vars, schedule |
| PUT | `/api/jobs/{id}` | Update job |
| DELETE | `/api/jobs/{id}` | Delete job |
| POST | `/api/jobs/{id}/enable` | Toggle enabled/disabled |
| POST | `/api/jobs/{id}/steps` | Add step |
| PUT | `/api/jobs/{id}/steps/{stepId}` | Update step |
| DELETE | `/api/jobs/{id}/steps/{stepId}` | Delete step |
| PUT | `/api/jobs/{id}/steps/reorder` | Reorder steps by ID list |
| GET | `/api/jobs/{id}/env-vars` | List job env vars |
| POST | `/api/jobs/{id}/env-vars` | Add env var |
| DELETE | `/api/jobs/{id}/env-vars/{envId}` | Delete env var |
| GET | `/api/jobs/{id}/schedule` | Get schedule |
| POST | `/api/jobs/{id}/schedule` | Create schedule |
| PUT | `/api/jobs/{id}/schedule` | Update schedule |
| DELETE | `/api/jobs/{id}/schedule` | Delete schedule |
| POST | `/api/jobs/{id}/schedule/enable` | Enable schedule |
| POST | `/api/jobs/{id}/schedule/disable` | Disable schedule |

---

## Phase 3b Acceptance Criteria

- [ ] `GET /api/jobs` returns paginated list; `search` param filters by job name
- [ ] `POST /api/jobs` with duplicate name returns `400`
- [ ] `GET /api/jobs/{id}` returns steps in `stepOrder` ascending order
- [ ] `DELETE /api/jobs/{id}` cascades and deletes all steps and env vars
- [ ] Step reorder reassigns `stepOrder` 1..N correctly and returns updated list
- [ ] Adding a step at an existing order shifts others up by 1
- [ ] Deleting a step renumbers remaining steps contiguously
- [ ] `POST /api/jobs/{id}/schedule` on a job that already has one returns `400`
- [ ] All endpoints return `ApiResponse<T>` wrapper
- [ ] Test each endpoint with Postman — export collection to repo

---

**Previous:** [Phase 3a — Envelope & DTOs](./PHASE-3a-API-Envelope-DTOs.md)  
**Next:** [Phase 3c — Execution, SSE & System Endpoints](./PHASE-3c-API-Execution-SSE-System.md)
