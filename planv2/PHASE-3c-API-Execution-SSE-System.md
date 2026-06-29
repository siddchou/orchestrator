# Phase 3c — API: Execution Controller, SSE Log Streaming & System Endpoints

> **Goal:** Implement the run trigger + run history controller, the SSE live log
> streaming endpoint, the global env var controller, and the system utility endpoints.
> After this sub-phase the full API surface is complete and testable end-to-end.

> **Depends on:** Phase 3a (DTOs, `ApiResponse`), Phase 3b (`JobDefinitionService`),
> Phase 2 (`JobLaunchService`, `RunStatus`)  
> **Produces:** `JobExecutionController`, `LogStreamController`, `SystemController`,
> `JobRunQueryService`

---

## Package Layout for This Sub-Phase

```
com.yourco.orchestrator/
├── api/
│   └── controller/
│       ├── JobExecutionController.java
│       ├── LogStreamController.java
│       └── SystemController.java
└── service/
    └── JobRunQueryService.java
```

---

## 3c.1 JobRunQueryService

Separates read-only run queries from the write-path `JobLaunchService`.

```java
// com.yourco.orchestrator.service.JobRunQueryService

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JobRunQueryService {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final DtoMapper mapper;

    public Page<JobRunSummary> listRuns(
            Long jobId, RunStatus status,
            LocalDate from, LocalDate to,
            Pageable pageable) {

        // Use JPA Specification to build dynamic WHERE clause
        Specification<JobRun> spec = Specification.where(null);

        if (jobId != null) {
            spec = spec.and((root, q, cb) ->
                cb.equal(root.get("jobDefinition").get("jobId"), jobId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) ->
                cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) ->
                cb.greaterThanOrEqualTo(
                    root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) ->
                cb.lessThan(
                    root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }

        return runRepo.findAll(spec, pageable).map(mapper::toSummary);
    }

    public JobRunDetail getRunDetail(Long runId) {
        JobRun run = runRepo.findById(runId)
            .orElseThrow(() -> new JobNotFoundException(runId));

        List<RunStepDetail> steps = runStepRepo
            .findByJobRun_RunIdOrderByStepOrderAsc(runId)
            .stream()
            .map(mapper::toDetail)
            .toList();

        long duration = 0;
        if (run.getStartedAt() != null && run.getEndedAt() != null) {
            duration = ChronoUnit.SECONDS.between(run.getStartedAt(), run.getEndedAt());
        }

        return new JobRunDetail(
            run.getRunId(),
            run.getJobDefinition().getJobId(),
            run.getJobDefinition().getJobName(),
            run.getStatus(),
            run.getTriggerType(),
            run.getTriggeredBy(),
            run.getStartedAt(),
            run.getEndedAt(),
            duration,
            steps
        );
    }

    public String getStepLog(Long runId, Long runStepId) {
        return runStepRepo.findById(runStepId)
            .filter(rs -> rs.getJobRun().getRunId().equals(runId))
            .map(JobRunStep::getLogOutput)
            .orElseThrow(() -> new JobNotFoundException(runStepId));
    }

    public JobRunSummary toSummary(JobRun run) {
        return mapper.toSummary(run);
    }
}
```

**Add `JpaSpecificationExecutor` to `JobRunRepository`:**

```java
public interface JobRunRepository
    extends JpaRepository<JobRun, Long>,
            JpaSpecificationExecutor<JobRun> {
    // existing methods from Phase 1 remain
}
```

---

## 3c.2 JobExecutionController

Handles triggering runs, querying run history, and cancellation.

```java
// com.yourco.orchestrator.api.controller.JobExecutionController

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobLaunchService launchService;
    private final JobRunQueryService runQueryService;

    // Trigger a manual run — returns 202 Accepted immediately (async)
    @PostMapping("/jobs/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<JobRunSummary> trigger(
            @PathVariable Long id,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "anonymous";
        JobRun run = launchService.launch(id, TriggerType.MANUAL, username);
        return ApiResponse.success(runQueryService.toSummary(run));
    }

    // List runs with optional filters
    @GetMapping("/runs")
    public ApiResponse<Page<JobRunSummary>> listRuns(
            @RequestParam(required = false)    Long jobId,
            @RequestParam(required = false)    RunStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
            runQueryService.listRuns(jobId, status, from, to,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
        );
    }

    // Run detail with step-level timeline
    @GetMapping("/runs/{runId}")
    public ApiResponse<JobRunDetail> getRun(@PathVariable Long runId) {
        return ApiResponse.success(runQueryService.getRunDetail(runId));
    }

    // Full log for a specific step (can be large — don't include in run detail)
    @GetMapping("/runs/{runId}/steps/{runStepId}/log")
    public ApiResponse<String> getStepLog(
            @PathVariable Long runId,
            @PathVariable Long runStepId) {
        return ApiResponse.success(runQueryService.getStepLog(runId, runStepId));
    }

    // Cancel a running job
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long runId) {
        launchService.cancel(runId);
        return ApiResponse.success(null);
    }
}
```

---

## 3c.3 SSE Live Log Streaming Controller

Streams `JavaExecStepExecutor` stdout in real time to the browser via Server-Sent Events.

```java
// com.yourco.orchestrator.api.controller.LogStreamController

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LogStreamController {

    private final JobLaunchService launchService;
    private final JobRunRepository runRepo;

    /**
     * GET /api/runs/{runId}/log-stream
     *
     * Returns a text/event-stream. The Angular client opens an EventSource to this URL.
     * Stream closes automatically when the run leaves RUNNING/PENDING state.
     * If the run is already complete when the client connects, closes immediately.
     */
    @GetMapping(
        value = "/runs/{runId}/log-stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamLog(@PathVariable Long runId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // no server-side timeout
        ConcurrentLinkedQueue<String> queue = launchService.getLiveLogQueue(runId);

        if (queue == null) {
            // Run finished before client connected — close stream immediately
            try {
                emitter.send(SseEmitter.event().name("done").data("RUN_ALREADY_COMPLETE"));
            } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        // Drain the queue on a Java 21 virtual thread so no carrier thread is blocked
        Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    String line = queue.poll();

                    if (line != null) {
                        emitter.send(SseEmitter.event().data(line));
                    } else {
                        // Queue empty — check if run has finished
                        JobRun run = runRepo.findById(runId).orElse(null);
                        if (run == null
                                || (run.getStatus() != RunStatus.RUNNING
                                 && run.getStatus() != RunStatus.PENDING)) {
                            // Drain any last lines that arrived just before completion
                            String remaining;
                            while ((remaining = queue.poll()) != null) {
                                emitter.send(SseEmitter.event().data(remaining));
                            }
                            emitter.send(SseEmitter.event().name("done").data("RUN_COMPLETE"));
                            emitter.complete();
                            break;
                        }
                        // Back-off when queue is empty but run is still active
                        Thread.sleep(200);
                    }
                }
            } catch (IOException ex) {
                // Client disconnected — silently end the virtual thread
                log.debug("SSE client disconnected for run {}", runId);
                emitter.completeWithError(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}
```

---

## 3c.4 SystemController — Global Env Vars & System Utilities

```java
// com.yourco.orchestrator.api.controller.SystemController

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final JobEnvVarRepository envVarRepo;
    private final DtoMapper mapper;

    // ----- Global Environment Variables -----

    @GetMapping("/env-vars/global")
    public ApiResponse<List<EnvVarResponse>> listGlobalEnvVars() {
        return ApiResponse.success(
            envVarRepo.findByIsGlobal("Y").stream()
                .map(mapper::toResponse)
                .toList()
        );
    }

    @PostMapping("/env-vars/global")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnvVarResponse> addGlobalEnvVar(
            @Valid @RequestBody EnvVarRequest request) {
        JobEnvVar var = JobEnvVar.builder()
            .jobDefinition(null)       // no job association
            .varName(request.varName())
            .varValue(request.varValue())
            .isGlobal("Y")
            .build();
        return ApiResponse.success(mapper.toResponse(envVarRepo.save(var)));
    }

    @DeleteMapping("/env-vars/global/{envId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobalEnvVar(@PathVariable Long envId) {
        envVarRepo.findById(envId)
            .filter(v -> "Y".equals(v.getIsGlobal()))
            .ifPresentOrElse(
                envVarRepo::delete,
                () -> { throw new JobNotFoundException(envId); }
            );
    }

    // ----- System Utilities -----

    /**
     * Validate that a given JAVA_HOME and workingDir exist and are accessible
     * on the server filesystem. Used by the Angular UI before saving a job.
     */
    @GetMapping("/system/env-validate")
    public ApiResponse<Map<String, String>> validateEnv(
            @RequestParam String javaHome,
            @RequestParam String workingDir) {
        Map<String, String> results = new LinkedHashMap<>();
        results.put("javaHome",    Files.isDirectory(Path.of(javaHome))    ? "OK" : "NOT_FOUND");
        results.put("javaBin",     Files.isExecutable(Path.of(javaHome, "bin/java")) ? "OK" : "NOT_EXECUTABLE");
        results.put("workingDir",  Files.isDirectory(Path.of(workingDir))  ? "OK" : "NOT_FOUND");
        results.put("workingDirWritable", Files.isWritable(Path.of(workingDir)) ? "OK" : "NOT_WRITABLE");
        return ApiResponse.success(results);
    }

    /**
     * Validate a Spring cron expression and return next 3 fire times.
     * Angular cron builder calls this on expression change.
     */
    @GetMapping("/system/cron-validate")
    public ApiResponse<Map<String, Object>> validateCron(
            @RequestParam String expression) {
        try {
            CronExpression parsed = CronExpression.parse(expression);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next1 = parsed.next(now);
            LocalDateTime next2 = next1 != null ? parsed.next(next1) : null;
            LocalDateTime next3 = next2 != null ? parsed.next(next2) : null;
            return ApiResponse.success(Map.of(
                "valid", true,
                "next1", next1 != null ? next1.toString() : "none",
                "next2", next2 != null ? next2.toString() : "none",
                "next3", next3 != null ? next3.toString() : "none"
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.success(Map.of(
                "valid", false,
                "error", ex.getMessage()
            ));
        }
    }
}
```

> **Note:** `CronExpression` is Spring Framework's own class
> (`org.springframework.scheduling.support.CronExpression`) — use this instead of
> the deprecated `CronSequenceGenerator`.

---

## 3c.5 Full Endpoint Reference for Phase 3

The complete API surface after 3a + 3b + 3c:

| Method | Path | Controller |
|--------|------|------------|
| GET | `/api/jobs` | `JobDefinitionController` |
| POST | `/api/jobs` | `JobDefinitionController` |
| GET | `/api/jobs/{id}` | `JobDefinitionController` |
| PUT | `/api/jobs/{id}` | `JobDefinitionController` |
| DELETE | `/api/jobs/{id}` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/enable` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/steps` | `JobDefinitionController` |
| PUT | `/api/jobs/{id}/steps/{stepId}` | `JobDefinitionController` |
| DELETE | `/api/jobs/{id}/steps/{stepId}` | `JobDefinitionController` |
| PUT | `/api/jobs/{id}/steps/reorder` | `JobDefinitionController` |
| GET | `/api/jobs/{id}/env-vars` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/env-vars` | `JobDefinitionController` |
| DELETE | `/api/jobs/{id}/env-vars/{envId}` | `JobDefinitionController` |
| GET | `/api/jobs/{id}/schedule` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/schedule` | `JobDefinitionController` |
| PUT | `/api/jobs/{id}/schedule` | `JobDefinitionController` |
| DELETE | `/api/jobs/{id}/schedule` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/schedule/enable` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/schedule/disable` | `JobDefinitionController` |
| POST | `/api/jobs/{id}/run` | `JobExecutionController` |
| GET | `/api/runs` | `JobExecutionController` |
| GET | `/api/runs/{runId}` | `JobExecutionController` |
| GET | `/api/runs/{runId}/steps/{runStepId}/log` | `JobExecutionController` |
| POST | `/api/runs/{runId}/cancel` | `JobExecutionController` |
| GET | `/api/runs/{runId}/log-stream` | `LogStreamController` |
| GET | `/api/env-vars/global` | `SystemController` |
| POST | `/api/env-vars/global` | `SystemController` |
| DELETE | `/api/env-vars/global/{envId}` | `SystemController` |
| GET | `/api/system/env-validate` | `SystemController` |
| GET | `/api/system/cron-validate` | `SystemController` |

---

## Phase 3c Acceptance Criteria

- [ ] `POST /api/jobs/{id}/run` returns `202 Accepted` and a run summary immediately
- [ ] A triggered run transitions from `PENDING` → `RUNNING` → `SUCCESS/FAILED` in the DB
- [ ] `GET /api/runs` filters by `jobId`, `status`, `from`, `to` independently and combined
- [ ] `GET /api/runs/{runId}/steps/{runStepId}/log` returns the full CLOB log content
- [ ] `POST /api/runs/{runId}/cancel` interrupts an active run within 5 seconds
- [ ] `GET /api/runs/{runId}/log-stream` streams lines in real time via SSE
- [ ] SSE stream sends a `done` event and closes when the run completes
- [ ] SSE stream closes cleanly if the client disconnects mid-stream (no thread leak)
- [ ] `GET /api/system/cron-validate` with a valid expression returns 3 future fire times
- [ ] `GET /api/system/cron-validate` with an invalid expression returns `valid: false`
- [ ] `GET /api/system/env-validate` correctly detects missing or non-executable paths

---

**Previous:** [Phase 3b — Job Definition Controller](./PHASE-3b-API-JobDefinition-Controller.md)  
**Next:** [Phase 4a — Scheduler Core](./PHASE-4a-Scheduler-Core.md)
