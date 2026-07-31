# Phase 3.1 — DAG Engine Design (Implementation Audit)

## Dependency Model — Implemented

The join table approach was chosen and implemented. Schema matches the original design:

```sql
-- V8__add_step_dependencies.sql
CREATE TABLE JOB_STEP_DEPENDENCY (
    DEPENDENCY_ID       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    STEP_ID             NUMBER NOT NULL REFERENCES JOB_STEP(STEP_ID) ON DELETE CASCADE,
    DEPENDS_ON_STEP_ID  NUMBER NOT NULL REFERENCES JOB_STEP(STEP_ID),
    EDGE_CONDITION      VARCHAR2(20) DEFAULT 'ON_SUCCESS'
                        CHECK (EDGE_CONDITION IN ('ON_SUCCESS', 'ON_FAILURE', 'ALWAYS')),
    CONSTRAINT UQ_STEP_DEP UNIQUE (STEP_ID, DEPENDS_ON_STEP_ID)
);
```

**Audit:** Schema is correct. FKs enforce referential integrity. Unique constraint prevents duplicate edges. Indexes on both directions support DAG traversal queries.

## DagExecutionEngine — As Implemented

### Execution Flow (`execute()` method)

```
1. loadDependencies()    — jobStepDependencyRepo.findByStepId(step.getStepId()) for each step
2. buildDag()            — construct Map<String, List<JobStepDependency>> upstream map
                           — construct Map<String, List<String>> downstream map
                           — identify root steps (no dependencies)
3. validateAcyclic()     — Kahn's algorithm: BFS from roots, count visited nodes
                           — if count != step count → CircularDependencyException thrown
4. executeConcurrent(5)  — ConcurrentHashMap for results, Semaphore(5), CountDownLatch(stepCount)
```

### What Works Correctly

- **Cycle detection:** Kahn's algorithm is correct — O(V+E), detects all cycles including self-loops (a self-loop creates a node with in-degree that can never reach zero).
- **Concurrency bounding:** Semaphore acquire before step execution, release in finally block — correct pattern.
- **Result collection:** `ConcurrentHashMap.put(stepId, result)` — thread-safe by construction. StepResult is an immutable record — reads see consistent snapshots.
- **Completion sync:** `CountDownLatch.await(timeout, SECONDS)` — run doesn't hang indefinitely.
- **Edge condition evaluation:** `signalDependents()` checks each downstream edge's condition against the upstream step's actual result status.

### Bugs Requiring Fixes

#### BUG-1: Empty upstreamOutputs (Line 626)

```java
.upstreamOutputs(Map.of())  // ← should pass resolution context step outputs
```

The `StepContext` built for each step has an empty `upstreamOutputs` map. The `ParamResolver` receives a `ResolutionContext` with an empty `stepOutputs` field. Cross-step template references (`${step.A.output.X}`) always resolve to null or the default value.

**Fix scope:** In the step context builder, construct `stepOutputs` from the `completedResults` ConcurrentHashMap. Filter to only include steps that are upstream dependencies of the current step (to avoid exposing unrelated step data).

```java
// Pseudocode fix:
Map<String, Map<String, Object>> stepOutputs = new HashMap<>();
for (JobStepDependency dep : dag.getUpstream().get(currentStepId)) {
    StepResult result = completedResults.get(dep.getDependsOnStepId());
    if (result != null) {
        stepOutputs.put(dep.getDependsOnStepId(), result.outputs());
    }
}
contextBuilder.upstreamOutputs(stepOutputs);
```

#### BUG-2: SKIPPED recorded as FAILED (Line 378)

When edge conditions are not satisfied, the dependent step is marked with `StepResult.failure()` instead of `StepStatus.SKIPPED`. This corrupts downstream skip propagation because a "FAILED" step satisfies ON_FAILURE edges.

**Fix scope:** Add `StepResult.skipped(String message)` factory method. Use it in the skip path.

#### BUG-3: Timing window too wide (Lines 286-287)

Start time captured at task submission, end time after signaling. Execution time includes template resolution and dependency signaling overhead.

**Fix scope:** Narrow timing window to wrap only the executor's `execute()` call.

#### BUG-4: No CANCELLED status

Pending steps on cancel are marked FAILED instead of CANCELLED.

**Fix scope:** Add `CANCELLED` to `StepStatus` enum. Check `cancelRequested` flag in the step task before marking FAILED.

## ParamResolver — As Implemented

The resolver handles three reference types:
- `${job.param.X}` → resolved from launch-time parameters map
- `${step.<id>.output.X}` → resolved from completed step outputs (broken by BUG-1)
- `${env.X}` → resolved from system env vars + job-specific env vars

Default value syntax: `${param?default_value}` — resolves to `default_value` when key is missing.

**Audit:** Regex pattern, resolution order, and null handling are correct. The class is stateless — thread-safe for concurrent use. Recursive resolution capped at 3 passes prevents infinite loops from self-referential templates.

## Backfill Migration (V9)

The PL/SQL block correctly:
1. Iterates jobs → steps ordered by stepOrder
2. Creates N-1 dependency rows per job (step N depends on step N-1)
3. Respects `continueOnFailure=Y` → `ALWAYS` edge condition
4. Leaves root step (lowest stepOrder) with no dependencies

**Audit:** Logic is correct for linear chains. All current jobs are linear, so this covers the existing data.

## Integration Points

### How DagExecutionEngine Replaces Sequential Loop

The engine is called from `JobLaunchService.launch()` instead of `JobExecutionOrchestrator.execute()`. The old orchestrator code is retained but not invoked for full job runs (may be used for single-step re-execution).

### Run Status Computation

After `runLatch.await()` completes, the engine computes run status from collected step results:
- All SUCCESS/SKIPPED → SUCCESS
- Any FAILED and no root failed → PARTIAL
- Root step failed → FAILED
