# Phase 3 — Code Review Findings

## Implementation Status

**Phase 3 is ~90% implemented.** The DAG engine, parameter resolver, dependency model, and migrations all exist in code. This document audits the existing implementation rather than speculating about future work.

### What Exists

| Component | File | Status |
|-----------|------|--------|
| DAG execution engine | `DagExecutionEngine.java` (672 lines) | Complete — concurrent execution, Kahn's cycle detection, semaphore bounding, CountDownLatch sync |
| Parameter resolver | `ParamResolver.java` | Complete — `${job.param.X}`, `${step.<id>.output.X}`, `${env.X}`, default value syntax |
| Dependency entity | `JobStepDependency.java` | Complete — join table with edge condition enum (ON_SUCCESS/ON_FAILURE/ALWAYS) |
| Dependency repository | `JobStepDependencyRepository.java` | Complete |
| Resolution context | `ResolutionContext.java` | Complete — jobParams, stepOutputs, envVars record |
| Step result | `StepResult.java` | Complete — status, outputs map, message, executionTime |
| Migration V8 | `V8__add_step_dependencies.sql` | Complete — JOB_STEP_DEPENDENCY table (Oracle) |
| Migration V9 | `V9__backfill_step_dependencies.sql` | Complete — stepOrder → dependency edges backfill |
| Unit tests | `DagExecutionEngineTest.java`, `ParamResolverTest.java` | Exist — cover basic paths |
| UI DAG canvas | `RunDagCanvasComponent.tsx` | Complete — read-only run DAG visualization |

### Core Architecture (As Implemented)

```
JobExecutionController.POST /api/jobs/{id}/run
  → JobLaunchService.launch(id, MANUAL, user, params)
    → DagExecutionEngine.execute(job, jobRun, params)
      1. loadDependencies() — fetch JOB_STEP_DEPENDENCY edges from repo
      2. buildDag() — construct upstream/downstream maps + root set
      3. validateAcyclic() — Kahn's algorithm, throws CircularDependencyException
      4. executeConcurrent(maxConcurrency=5)
         - ConcurrentHashMap<String, StepResult> for step results
         - Semaphore(5) to bound concurrent step execution
         - CountDownLatch(jobStepCount) for completion sync
         - ThreadPoolTaskExecutor (core=10, max=20, queue=50)

      5. submitStep() — per-step: acquire semaphore → execute → signal dependents → release semaphore
      6. signalDependents() — check edge conditions, fire downstream steps with resolved templates
```

## Bugs Found

### BUG-1: Empty upstreamOutputs in StepContext (High)

**File:** `DagExecutionEngine.java:626`

```java
.upstreamOutputs(Map.of())  // ← hardcoded empty map
```

When building `StepContext` for step execution, the engine passes an empty map for `upstreamOutputs`. This means downstream steps cannot access upstream step outputs via `${step.<id>.output.X}` templates — the resolution context has no data to resolve against.

**Impact:** Parameter templating across steps is broken. Step B cannot read Step A's output even if the dependency edge exists and Step A succeeded.

**Fix:** Build a `Map<String, Map<String, Object>>` from `stepResults` at StepContext build time. For each completed upstream step of the current step, include its outputs.

### BUG-2: SKIPPED steps recorded as FAILED (Medium)

**File:** `DagExecutionEngine.java:378`

```java
StepResult.failure("Skipped - upstream condition not met")
```

When a dependent step is skipped because an upstream edge condition was not satisfied, the code uses `StepResult.failure()` which sets status to `FAILED`. This should use `StepStatus.SKIPPED`.

**Impact:** Run detail page shows SKIPPED steps as FAILED. Metrics and audit logs are inaccurate. Downstream skip propagation logic may misfire (a "failed" step triggers ON_FAILURE edges when it shouldn't).

**Fix:** Use a dedicated `StepResult.skipped()` factory method or construct with `StepStatus.SKIPPED`.

### BUG-3: Timing inaccuracy (Low)

**File:** `DagExecutionEngine.java:286-287`

Start time is captured at task submission time, not at actual executor invocation. End time is after signalDependents. This inflates reported execution times by including dependency signaling overhead and template resolution time.

**Impact:** Execution metrics are slightly inflated (typically 10-50ms per step). Not visible to users for steps that take seconds, but noticeable for fast steps.

**Fix:** Capture `startTime` immediately before calling the executor, and capture end time immediately after. Exclude template resolution and signaling from the timing window.

### BUG-4: Cancel runs as failure, not cancellation (Low)

When a run has `cancelRequested = true`, the engine marks pending steps as FAILED rather than CANCELLED. There's no distinct CANCELLED status handling in the DAG path.

**Impact:** Cancelled runs appear identical to failed runs in audit logs and metrics. No way to distinguish "user cancelled" from "step crashed."

**Fix:** Check `cancelRequested` before marking a step FAILED. If cancel is requested, use a CANCELLED status (may require adding this enum value to StepStatus).

## Observations

### Thread Safety
The existing concurrency primitives are well-chosen:
- `ConcurrentHashMap` for step results — correct for put/get pattern
- `Semaphore(5)` for bounding concurrent steps — prevents DB overload
- `CountDownLatch` for DAG completion — simple, effective
- `ThreadPoolTaskExecutor` (Spring-managed) — proper lifecycle

**One concern:** `StepContext.envVars` is a mutable HashMap shared from ExecutionContext. Under concurrent execution, two steps running in parallel could see each other's ENV_SETUP mutations. The fix is to give each step its own copy of envVars and propagate changes via StepResult.outputs (explicit data flow).

### CredentialResolver
The lambda-based credential resolver creates a new DB transaction per call — thread-safe. However, `DecryptionService` may reuse a `javax.crypto.Cipher` instance, which is **not** thread-safe. Verify and fix if needed.

### Cancellation under concurrency
Current `Future.cancel(true)` only interrupts one thread. Under DAG execution, steps run on different pool threads. The `cancelRequested` flag approach works but needs to be checked at more points: before step execution AND after dependency latch release (to avoid waiting indefinitely for a cancelled upstream).

## Remaining Work Items

1. **Fix BUG-1** — Pass actual upstream outputs to StepContext (~2 hours)
2. **Fix BUG-2** — Use SKIPPED status for skipped steps (~1 hour)
3. **Fix BUG-3** — Tighten timing window (~0.5 hours)
4. **Fix BUG-4** — Add CANCELLED status handling (~2 hours)
5. **Verify envVars thread safety** — Per-step copy under concurrent execution (~1 hour)
6. **Add e2e test for diamond DAG concurrency** — Prove parallel execution actually happens (~3 hours)
7. **Clean up legacy comments** — Remove "Phase 2" references in Phase 3 code (~0.5 hours)

**Total remaining effort: ~9-12 hours (2 story points)**
