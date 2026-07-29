<!-- FILE: phase3-code-review-findings.md -->
# Phase 3 — Code Review Findings

## 1. Execution Loop (Sequential, stepOrder-based)

**File:** `../../src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java`

- **Lines 66–85:** The `execute()` method filters enabled steps then iterates them sequentially in a plain `for` loop:
  ```java
  var steps = job.getSteps().stream()
      .filter(s -> "Y".equals(s.getEnabled()))
      .toList();
  for (JobStep step : steps) { ... }
  ```
- **Ordering mechanism:** Steps are ordered by `stepOrder` (Integer column on `JOB_STEP`). The ordering comes from the entity relationship: `JobRun.runSteps` uses `@OrderBy("stepOrder ASC")` ([JobRun.java:53](src/main/java/com/novakai/orchestrator/domain/entity/JobRun.java:53)), so JPA returns steps sorted by this column.
- **Line 280:** When creating a `JobRunStep`, the orchestrator copies `step.getStepOrder()` directly into the run step record.
- **No dependency columns exist** beyond `stepOrder`. There is no `depends_on`, no join table, no edge conditions.

## 2. Concurrency Model

**File:** `../../src/main/java/com/novakai/orchestrator/engine/config/AsyncConfig.java`

| Bean | Type | Pool Size | Queue | Purpose |
|------|------|-----------|-------|---------|
| `jobTaskExecutor` | `ThreadPoolTaskExecutor` | core=10, max=20 | 50 | Job execution (one Future per run) |
| `taskScheduler` | `ThreadPoolTaskScheduler` | 5 | n/a | Cron scheduling |

- **Rejection policy:** `CallerRunsPolicy` ([AsyncConfig.java:30](src/main/java/com/novakai/orchestrator/engine/config/AsyncConfig.java:30))
- **MDC propagation:** Task decorator copies MDC context at submission time ([AsyncConfig.java:33-44](src/main/java/com/novakai/orchestrator/engine/config/AsyncConfig.java:33))
- Currently each job run = one `Future<?>` submitted to the pool. Steps within a run execute sequentially on that same thread.

**File:** `../../src/main/java/com/novakai/orchestrator/engine/JobLaunchService.java`

- Lines 50-53: Three `ConcurrentHashMap`s track active state per run:
  - `activeFutures<Long, Future<?>>` — the async handle for cancellation
  - `activeContexts<Long, ExecutionContext>` — mutable cancel flag + env vars
  - `liveLogQueues<Long, BlockingQueue<String>>` — SSE log streaming

## 3. StepResult / StepContext Shape (Post-Phase 1)

**File:** `../../src/main/java/com/novakai/orchestrator/engine/spi/StepResult.java`

```java
public record StepResult(
    StepStatus status,                    // SUCCESS / FAILED / SKIPPED
    Map<String, Object> outputs,          // structured outputs for Phase 3 templating
    String message,                       // human-readable summary
    Duration executionTime                // wall-clock time
)
```

- `StepStatus.SKIPPED` is already defined but not yet used ([StepStatus.java:9](src/main/java/com/novakai/orchestrator/engine/spi/StepStatus.java:9))
- Backward-compat methods: `isSuccess()`, `getExitCode()` (reads from outputs map), `getLogOutput()` (returns message)

**File:** `../../src/main/java/com/novakai/orchestrator/engine/spi/StepContext.java`

Key fields relevant to Phase 3:
- `Map<String, StepResult> upstreamOutputs` — **currently always empty** ([JobExecutionOrchestrator.java:237](src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java:237): `.upstreamOutputs(Map.of())`)
- `Map<String, Object> resolvedParams` — exists but not populated from anywhere yet
- `volatile boolean cancelRequested` — mutable across threads via setter

**File:** `../../src/main/java/com/novakai/orchestrator/engine/ExecutionContext.java`

- Legacy context object still used by `JobLaunchService`. Contains `cancelRequested`, `envVars`, `liveLogQueue`, etc.
- `JobExecutionOrchestrator.buildStepContext()` bridges ExecutionContext → StepContext (line 212)

## 4. Run Trigger Endpoint

**File:** `../../src/main/java/com/novakai/orchestrator/api/controller/JobExecutionController.java`

```java
@PostMapping("/jobs/{id}/run")          // line 33
public ApiResponse<JobRunSummary> trigger(
        @PathVariable Long id,
        Authentication auth)             // NO request body parameter
```

- Currently accepts **no request body** — just path variable and auth
- Delegates to `launchService.launch(id, TriggerType.MANUAL, username)` which takes no parameters map
- There is also a name-based variant: `POST /jobs/name/{name}/run` (line 45) with same signature

## 5. Existing Templating Logic

**File:** `../../src/main/java/com/novakai/orchestrator/engine/executors/SftpStepExecutor.java`

- Lines 297-307: A simple string-replacement method for remote file naming:
  ```java
  return template.replace("${fileName}", nameWithoutExt)
                 .replace("${fileExtension}", extension)
                 .replace("${timestamp}", String.valueOf(System.currentTimeMillis()));
  ```
- This is **executor-specific**, not a centralized resolver. No `${job.param.X}` or `${step.<id>.output.X}` pattern exists anywhere else.

**No other templating/variable-substitution logic found in the codebase.** The `@Value("${...}")` usages are Spring property placeholders only.

## 6. Flyway Migration Versions

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP, JOB_ENV_VAR |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP |
| V3 | `V3__create_schedule_and_credential.sql` | JOB_SCHEDULE, JOB_CREDENTIAL, AUDIT_LOG |
| V4 | `V4__create_app_user.sql` | App user table |
| V5 | `V5__add_env_setup_to_job_definition.sql` | Env setup column |
| V6 | `V6__relax_step_type_constraint.sql` | Removes step type CHECK constraint |
| V7 | `V7__add_multi_tenancy.sql` | TEAM, USER_TEAM tables |

**Next free version: V8**

## 7. Additional Findings

- **Database dialect:** Migrations use Oracle syntax (`NUMBER GENERATED ALWAYS AS IDENTITY`, `VARCHAR2`, `CLOB`, `SYSTIMESTAMP`). Phase 3 SQL must be Oracle-compatible.
- **`continueOnFailure` field:** Currently a `CHAR(1)` Y/N column on JOB_STEP, checked in the orchestrator loop (line 80). This is a per-step flag that says "if this step fails, keep going to the next step." In Phase 3's DAG model, this concept maps naturally to edge conditions.
- **`markRemainingStepsCancelled`:** Uses `JobRunStepRepository.findIncompleteStepsByRunId()` — finds PENDING/RUNNING steps and marks them CANCELLED. This logic needs updating for DAG (cancel unsatisfied dependents, not just "remaining by order").
- **Thread safety of envVars:** `StepContext.envVars` is a mutable `HashMap` ([StepContext.java:47](src/main/java/com/novakai/orchestrator/engine/spi/StepContext.java:47): `new HashMap<>(oldCtx.getEnvVars())`). ENV_SETUP executor mutates this map. Under concurrent step execution, shared envVars from the same ExecutionContext would be a data race.

## 8. [NOT FOUND] Items

- **No `ParamResolver` class exists** — confirmed absent
- **No DAG engine exists** — confirmed absent
- **No `JOB_STEP_DEPENDENCY` table or column** — confirmed absent
- **No request body DTO for the run endpoint** — the endpoint takes no body at all currently
