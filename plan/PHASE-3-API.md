# Phase 3 — REST API Layer

> **Goal:** Expose all orchestration functionality through a clean REST API with a
> consistent response envelope. Also implement SSE-based live log streaming.
> Test all endpoints with Postman or curl before moving to Phase 4.

---

## 3.1 Standard Response Envelope

All endpoints return this wrapper. Enforce it via a global `@ControllerAdvice`.

```java
public record ApiResponse<T>(
    String status,      // "SUCCESS" or "ERROR"
    T data,
    String error,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("ERROR", null, message, LocalDateTime.now());
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(JobNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(JobAlreadyRunningException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        return new ApiResponse<>("ERROR", errors, "Validation failed", LocalDateTime.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        return ApiResponse.error("Internal error: " + ex.getMessage());
    }
}
```

---

## 3.2 DTOs

### Job Definition

```java
// Request
public record JobDefinitionRequest(
    @NotBlank String jobName,
    String description,
    @NotBlank String workingDir
) {}

// Response (flattened — no lazy collections)
public record JobDefinitionResponse(
    Long jobId,
    String jobName,
    String description,
    String workingDir,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<JobStepResponse> steps,
    List<EnvVarResponse> envVars,
    JobScheduleResponse schedule
) {}
```

### Job Step

```java
public record JobStepRequest(
    @NotBlank String stepName,
    @NotNull Integer stepOrder,
    @NotNull StepType stepType,
    @NotNull String stepConfig,       // validated as JSON
    boolean continueOnFailure,
    boolean enabled
) {}

public record JobStepResponse(
    Long stepId,
    String stepName,
    Integer stepOrder,
    StepType stepType,
    String stepConfig,
    boolean continueOnFailure,
    boolean enabled
) {}

public record StepReorderRequest(
    @NotEmpty List<Long> stepIds  // in desired order
) {}
```

### Job Run

```java
// Response for run list (summary)
public record JobRunSummary(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    TriggerType triggerType,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
) {}

// Response for run detail
public record JobRunDetail(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    TriggerType triggerType,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    List<RunStepDetail> steps
) {}

public record RunStepDetail(
    Long runStepId,
    String stepName,
    StepType stepType,
    Integer stepOrder,
    RunStatus status,
    Integer exitCode,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    long durationSeconds
    // note: logOutput NOT included here — use separate endpoint to avoid large payloads
) {}
```

---

## 3.3 Job Definition Controller

```java
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobDefinitionController {

    private final JobDefinitionService jobService;

    @GetMapping
    public ApiResponse<Page<JobDefinitionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(jobService.listJobs(search, PageRequest.of(page, size)));
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

## 3.4 Job Execution Controller

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    @PostMapping("/jobs/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<JobRunSummary> trigger(
            @PathVariable Long id,
            Authentication auth) {
        JobRun run = launchService.launch(id, TriggerType.MANUAL, auth.getName());
        return ApiResponse.success(runQueryService.toSummary(run));
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
    public ApiResponse<Void> cancel(@PathVariable Long runId) {
        launchService.cancel(runId);
        return ApiResponse.success(null);
    }
}
```

---

## 3.5 SSE Live Log Streaming

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LogStreamController {

    private final JobLaunchService launchService;
    private final JobRunRepository runRepo;

    /**
     * GET /api/runs/{runId}/log-stream
     * Returns a text/event-stream that tails live output while the run is active.
     * Closes automatically when the run completes.
     */
    @GetMapping(value = "/runs/{runId}/log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLog(@PathVariable Long runId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // no timeout
        ConcurrentLinkedQueue<String> queue = launchService.getLiveLogQueue(runId);

        if (queue == null) {
            // Run already finished — serve from DB
            emitter.complete();
            return emitter;
        }

        // Poll queue and push to SSE on a virtual thread
        Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    String line = queue.poll();
                    if (line != null) {
                        emitter.send(SseEmitter.event().data(line));
                    } else {
                        // Check if run is still active
                        JobRun run = runRepo.findById(runId).orElse(null);
                        if (run != null && run.getStatus() != RunStatus.RUNNING
                                       && run.getStatus() != RunStatus.PENDING) {
                            emitter.send(SseEmitter.event().name("done").data("RUN_COMPLETE"));
                            emitter.complete();
                            break;
                        }
                        Thread.sleep(250);  // back-off when queue is empty
                    }
                }
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}
```

---

## 3.6 Global Config Controller

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final JobEnvVarRepository envVarRepo;
    private final JobEnvVarMapper mapper;

    // Global env vars
    @GetMapping("/env-vars/global")
    public ApiResponse<List<EnvVarResponse>> listGlobal() {
        return ApiResponse.success(envVarRepo.findByIsGlobal("Y").stream()
            .map(mapper::toResponse).toList());
    }

    @PostMapping("/env-vars/global")
    public ApiResponse<EnvVarResponse> addGlobal(@Valid @RequestBody EnvVarRequest req) {
        // isGlobal forced to Y
        ...
    }

    @DeleteMapping("/env-vars/global/{envId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobal(@PathVariable Long envId) { ... }

    // System health
    @GetMapping("/system/health")
    public ApiResponse<Map<String, Object>> health() {
        // Check DB, working dirs, Java binary
        ...
    }

    @GetMapping("/system/env-validate")
    public ApiResponse<Map<String, String>> validateEnv(
            @RequestParam String javaHome,
            @RequestParam String workingDir) {
        Map<String, String> results = new LinkedHashMap<>();
        results.put("javaHome", Files.isDirectory(Path.of(javaHome)) ? "OK" : "NOT_FOUND");
        results.put("javaBin", Files.isExecutable(Path.of(javaHome, "bin/java")) ? "OK" : "NOT_EXECUTABLE");
        results.put("workingDir", Files.isDirectory(Path.of(workingDir)) ? "OK" : "NOT_FOUND");
        return ApiResponse.success(results);
    }
}
```

---

## 3.7 CORS Configuration

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:4200")   // Angular dev server
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

---

## 3.8 Full Endpoint Reference

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
| PUT | `/api/jobs/{id}/steps/reorder` | Reorder steps |
| GET | `/api/jobs/{id}/env-vars` | List job env vars |
| POST | `/api/jobs/{id}/env-vars` | Add env var |
| DELETE | `/api/jobs/{id}/env-vars/{envId}` | Delete env var |
| GET | `/api/jobs/{id}/schedule` | Get schedule |
| POST | `/api/jobs/{id}/schedule` | Create schedule |
| PUT | `/api/jobs/{id}/schedule` | Update schedule |
| DELETE | `/api/jobs/{id}/schedule` | Delete schedule |
| POST | `/api/jobs/{id}/schedule/enable` | Enable schedule |
| POST | `/api/jobs/{id}/schedule/disable` | Disable schedule |
| POST | `/api/jobs/{id}/run` | Manual trigger |
| GET | `/api/runs` | List runs (filter by job, status, date) |
| GET | `/api/runs/{runId}` | Run detail with step timeline |
| GET | `/api/runs/{runId}/steps/{stepId}/log` | Full step log |
| POST | `/api/runs/{runId}/cancel` | Cancel running job |
| GET | `/api/runs/{runId}/log-stream` | SSE live log stream |
| GET | `/api/env-vars/global` | List global env vars |
| POST | `/api/env-vars/global` | Add global env var |
| DELETE | `/api/env-vars/global/{envId}` | Delete global env var |
| GET | `/api/system/health` | System health check |
| GET | `/api/system/env-validate` | Validate paths and Java binary |

---

## Phase 3 Acceptance Criteria

- [ ] All endpoints return `ApiResponse<T>` wrapper consistently
- [ ] `POST /api/jobs/{id}/run` returns `202 Accepted` immediately (async)
- [ ] `GET /api/runs/{runId}/log-stream` streams live output during an active run
- [ ] SSE stream sends `done` event and closes when the run completes
- [ ] `POST /api/runs/{runId}/cancel` stops the job within 5 seconds
- [ ] Validation errors return `400` with field-level messages
- [ ] `JobNotFoundException` returns `404`
- [ ] `JobAlreadyRunningException` returns `409`
- [ ] All endpoints tested via Postman collection (export collection to repo)

---

**Previous:** [Phase 2 — Engine](./PHASE-2-Engine.md)  
**Next:** [Phase 4 — Scheduling](./PHASE-4-Scheduling.md)
