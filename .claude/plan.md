# Phase 3 Remaining Tasks Implementation Plan

## Overview
Three remaining items from the Phase 3 DAG engine audit. All are self-contained with no cross-task dependencies.

---

## Task 14: Dependency CRUD API (~6 SP)

**Goal:** Allow users to manage step dependencies through REST endpoints, following existing patterns in `JobDefinitionController` and `JobDefinitionService`.

### New Files

**1. `api/dto/StepDependencyRequest.java`**
- Record with `Long dependsOnStepId` + `String edgeCondition` (EdgeCondition enum name)
- Validation: `dependsOnStepId` required, `edgeCondition` defaults to "ON_SUCCESS"

**2. `api/dto/StepDependencyResponse.java`**
- Record with `Long stepDependencyId`, `Long dependsOnStepId`, `String dependsOnStepName`, `String edgeCondition`

### Changes to Existing Files

**3. `JobDefinitionService.java`** — Add 4 methods:
- Inject `JobStepDependencyRepository` + `JobStepRepository` (already has JobStepRepo via existing field)
- `List<StepDependencyResponse> getDependencies(Long jobId, Long stepId)` — fetch deps for a step, validate ownership
- `void setDependencies(Long jobId, Long stepId, List<StepDependencyRequest> requests)` — replace all deps:
  - Validate `stepId` belongs to `jobId`
  - Reject self-reference (`dependsOnStepId == stepId`)
  - Validate each `dependsOnStepId` exists and belongs to same job
  - **Cycle detection**: build a temporary graph with proposed edges + existing unrelated edges, run Kahn's algorithm. throw `CircularDependencyException` if cycle found
  - Delete current deps for the step, insert new ones
- Validation helper: `validateNoCycle(List<JobStep> steps, Long targetStepId, List<StepDependencyRequest> newDeps)` — constructs adjacency from all enabled steps' existing deps (excluding target step's), adds proposed edges, runs Kahn's

**4. `JobDefinitionController.java`** — Add 2 endpoints under existing `/api/jobs` mapping:
- `GET /{id}/steps/{stepId}/dependencies` → `ApiResponse<List<StepDependencyResponse>>`
- `PUT /{id}/steps/{stepId}/dependencies`, body `List<StepDependencyRequest>` → `ApiResponse<Void>`

### Cycle Detection Strategy
Reuse Kahn's algorithm logic from `DagExecutionEngine.validateAcyclic()`. Since that method is private, extract the core cycle-checking into a small local helper in the service — it's simpler to duplicate ~20 lines of graph traversal than to refactor the engine. The service operates on job **definition** time (no execution context), so this is conceptually separate from runtime validation.

---

## Task 15: SftpStepExecutor Template Cleanup (~2 SP)

**Goal:** Replace hardcoded `.replace("${fileName}", ...)` in `SftpStepExecutor.resolveRemoteFileName()` with generic ParamResolver usage.

### Changes to Existing Files

**1. `ParamResolver.java`** — Add static method:
```java
public static String resolveSimple(String template, Map<String, Object> vars)
```
- Matches `${key}` patterns (no namespace prefix like `job.param.`)
- Supports default values: `${key?defaultVal}`
- Uses same regex pattern approach as existing `resolve()` method
- Returns string with all matched templates replaced

**2. `SftpStepExecutor.java`** — In `resolveRemoteFileName()`:
- Build a `Map<String, Object>` with keys: `"fileName"`, `"fileExtension"`, `"timestamp"`
- Call `ParamResolver.resolveSimple(template, vars)` instead of chained `.replace()` calls
- Same behavior, cleaner pattern

---

## Task 13b: Root Step Failed → FAILED Status (~1 SP)

**Goal:** Distinguish root step failure (entire job failed from the start) from non-root step failure (job partially succeeded).

### Changes to Existing Files

**1. `DagExecutionEngine.java`**:
- Pass `graph.roots` into `finalizeRun()` as a new parameter
- In `finalizeRun()`: check if any failed step ID is in the roots set → set run status to `FAILED`
- Non-root failure with other successes → keep `PARTIAL` behavior
- Signature change: `finalizeRun(ctx, run, stepResults, runSteps, startTimes, remaining, rootIds)`

---

## Implementation Order

1. **Task 13b** first — smallest change, single file, lowest risk
2. **Task 15** second — two files, isolated from API layer
3. **Task 14** last — most files, requires cycle detection logic, new DTOs + endpoints

Total estimated effort: ~9 story points across 3 tasks.
