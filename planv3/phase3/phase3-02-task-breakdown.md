<!-- FILE: phase3-02-task-breakdown.md -->
# Phase 3.2 — Task Breakdown

Each task is PR-sized (1-3 days for a senior developer). Tasks marked with ⚡ can be worked in parallel.

---

### Task 1: JOB_STEP_DEPENDENCY table + entity

**Files Touched:** `src/main/resources/db/migration/V8__add_step_dependencies.sql`, new `domain/entity/JobStepDependency.java`, new `repository/JobStepDependencyRepository.java`

**Definition of Done:**
- Flyway migration V8 creates the table with FKs, unique constraint, indexes (see phase3-01-dag-engine-design.md schema)
- JPA entity maps correctly; repository exposes `findByStepId(Long)` and `findDependenciesForStep(Long dependsOnStepId)`
- Migration runs cleanly on a fresh database

**Test to Add:** `JobStepDependencyRepositoryTest` — create dependency, query by both FK directions, verify unique constraint rejection

**Depends On:** Nothing

---

### Task 2: Backfill migration — convert stepOrder chains to dependencies

**Files Touched:** `src/main/resources/db/migration/V9__backfill_step_dependencies.sql`

**Definition of Done:**
- SQL PL/SQL block iterates each job's steps ordered by stepOrder
- For each adjacent pair (step N, step N+1), inserts a JOB_STEP_DEPENDENCY row with ON_SUCCESS condition
- Step 1 (lowest stepOrder) gets no dependencies (it's a root step)
- Existing `continueOnFailure=N` behavior preserved: all edges are ON_SUCCESS

**Test to Add:** Integration test that creates a job with 4 steps, runs the backfill logic programmatically, verifies 3 dependency rows exist forming a chain

**Depends On:** Task 1

---

### Task 3: ParamResolver class

**Files Touched:** new `engine/template/ParamResolver.java`, new `engine/template/ResolutionContext.java`

**Definition of Done:**
- Regex-based resolver handles `${job.param.X}`, `${step.<id>.output.X}`, `${env.X}` patterns
- Unresolved references left as-is with warning log
- Recursive resolution (up to 3 passes)
- Handles null template, empty template, no-template-string gracefully

**Test to Add:** `ParamResolverTest` — unit tests for each reference type, unresolved references, nested resolution, edge cases (null values, special characters in replacement)

**Depends On:** Nothing ⚡

---

### Task 4: API changes — run endpoint accepts parameters

**Files Touched:** `api/controller/JobExecutionController.java`, new `api/dto/JobRunRequest.java`, `engine/JobLaunchService.java`

**Definition of Done:**
- `POST /api/jobs/{id}/run` accepts optional request body: `{ "parameters": { "env": "staging", "date": "2026-07-25" } }`
- Backward compatible: body is optional; omitting it works as before
- Parameters passed through to ExecutionContext (new field) and ultimately to DagExecutionEngine
- Name-based variant (`/jobs/name/{name}/run`) also updated

**Test to Add:** `JobExecutionControllerTest` — POST with parameters, POST without body, verify parameters reach launch service

**Depends On:** Nothing ⚡

---

### Task 5: ExecutionContext / StepContext parameter threading

**Files Touched:** `engine/ExecutionContext.java`, `engine/spi/StepContext.java`, `engine/JobLaunchService.java`

**Definition of Done:**
- ExecutionContext gains `Map<String, Object> runParameters` field
- JobLaunchService.buildContext() populates this from the launch method parameter
- StepContext builder receives resolved parameters and makes them available to ParamResolver
- Thread-safety: maps are immutable copies (Map.copyOf / Collections.unmodifiableMap)

**Test to Add:** Verify parameters flow from controller → launch service → ExecutionContext → StepContext via integration test

**Depends On:** Task 4

---

### Task 6: DagExecutionEngine — DAG building + cycle detection

**Files Touched:** new `engine/DagExecutionEngine.java`, new `engine/exception/CircularDependencyException.java`

**Definition of Done:**
- `buildDag(JobDefinition)` constructs an in-memory graph from JobStep + JobStepDependency entities
- Cycle detection throws `CircularDependencyException` with the cycle path described
- Orphan steps (no deps, no dependents, and not the only step) logged as warnings but allowed
- Single-step jobs work correctly (one root, no edges)

**Test to Add:** `DagExecutionEngineBuildTest` — build DAG from test fixtures: linear chain, diamond, circular (expect exception), single step, orphan

**Depends On:** Task 1

---

### Task 7: DagExecutionEngine — concurrent execution with semaphore bounding

**Files Touched:** `engine/DagExecutionEngine.java`

**Definition of Done:**
- Steps with satisfied dependencies execute concurrently up to `maxConcurrentSteps` (Semaphore-bounded)
- Uses the existing `jobTaskExecutor` pool (not a new ExecutorService)
- Each step's task waits on its dependency latch before acquiring the semaphore
- `CountDownLatch` per run ensures all steps complete before the run status is set

**Test to Add:** `DagExecutionEngineConcurrencyTest` — mock executors with measurable delay, verify independent branches run concurrently (start times overlap)

**Depends On:** Task 6

---

### Task 8: Edge condition evaluation + SKIPPED propagation

**Files Touched:** `engine/DagExecutionEngine.java`, `domain/enums/RunStatus.java` (add SKIPPED if not present)

**Definition of Done:**
- ON_SUCCESS edge: downstream runs only if upstream StepResult status is SUCCESS
- ON_FAILURE edge: downstream runs only if upstream status is FAILED
- ALWAYS edge: downstream runs regardless
- When condition not met, step marked SKIPPED (StepStatus.SKIPPED already exists)
- SKIPPED propagates: a step with ON_SUCCESS dep on a SKIPPED step is also SKIPPED

**Test to Add:** `EdgeConditionEvaluationTest` — test each condition type, mixed conditions on same step, SKIPPED propagation chain

**Depends On:** Task 7

---

### Task 9: ParamResolver wiring into execution pipeline

**Files Touched:** `engine/DagExecutionEngine.java`, `engine/JobExecutionOrchestrator.java` (or replace orchestrator call site)

**Definition of Done:**
- Before each step's executor.execute(), the step's config JSON string values are resolved through ParamResolver
- ResolutionContext built from: job parameters, completed upstream results, env vars
- Only string values in the config map are resolved (numbers, booleans left as-is)
- Resolved config passed to StepContext; original config logged for debugging

**Test to Add:** Integration test with a job that has `${job.param.X}` and `${step.<id>.output.X}` templates, verify executor receives resolved values

**Depends On:** Task 3, Task 7

---

### Task 10: Thread safety — StepContext per-step isolation

**Files Touched:** `engine/spi/StepContext.java`, `engine/DagExecutionEngine.java`

**Definition of Done:**
- Each concurrent step gets its own StepContext instance (no shared mutable state)
- envVars map: each step gets a copy; ENV_SETUP mutations don't leak to siblings
- cancelRequested flag: uses volatile (already is in StepContext) or AtomicBoolean
- logSink: the BlockingQueue is shared per run but `queue.add()` is thread-safe for LinkedBlockingQueue — verify and document

**Test to Add:** Concurrent step test where two ENV_SETUP steps set different env vars; verify no cross-contamination

**Depends On:** Task 7

---

### Task 11: Cancellation semantics for concurrent DAG

**Files Touched:** `engine/DagExecutionEngine.java`, `engine/JobLaunchService.java`

**Definition of Done:**
- Cancel request interrupts all running step futures
- Pending steps (waiting on dependency latch) check cancel flag and exit without executing
- `markRemainingStepsCancelled` updated to mark incomplete steps by runId (logic already works by runId, no change needed for the DB part, but the in-flight future cancellation is new)
- Run status set to CANCELLED in finally block

**Test to Add:** Cancel mid-run with concurrent steps; verify running step interrupted, pending steps never start, completed steps retain their result

**Depends On:** Task 7

---

### Task 12: Replace orchestrator's sequential loop with DAG engine

**Files Touched:** `engine/JobLaunchService.java`, `engine/JobExecutionOrchestrator.java` (deprecated or adapted)

**Definition of Done:**
- JobLaunchService.launch() calls DagExecutionEngine.execute() instead of JobExecutionOrchestrator.execute()
- JobExecutionOrchestrator retained for single-step re-execution (backdoor debugging feature) but not used for full job runs
- Run status determination updated: SUCCESS if all non-skipped steps succeeded, PARTIAL if any failed, FAILED if root step failed

**Test to Add:** Regression test — run a backfilled linear job, verify identical step execution order and final status as pre-migration

**Depends On:** Tasks 8, 9, 10, 11

---

### Task 13: Run status computation for DAG

**Files Touched:** `engine/DagExecutionEngine.java`

**Definition of Done:**
- After all steps complete, compute run status from step results:
  - All steps SUCCESS or SKIPPED → SUCCESS
  - Any step FAILED and no root step failed → PARTIAL
  - Root step (no dependencies) failed → FAILED
  - Cancelled during execution → CANCELLED
- SKIPPED steps don't count as failures for status computation

**Test to Add:** Status computation unit test with various combinations of SUCCESS/FAILED/SKIPPED across root and non-root steps

**Depends On:** Task 8

---

### Task 14: Job definition API — dependency CRUD

**Files Touched:** `api/controller/JobStepController.java` (or new controller), `api/dto/StepDependencyDto.java`, service layer

**Definition of Done:**
- `PUT /api/jobs/{id}/steps/{stepId}/dependencies` — set dependencies for a step (array of `{dependsOnStepId, edgeCondition}`)
- `GET /api/jobs/{id}/steps/{stepId}/dependencies` — list current dependencies
- Validation: reject circular dependency creation, reject self-reference
- Cycle check on write prevents saving an invalid job definition

**Test to Add:** Controller test for CRUD operations, cycle rejection on PUT

**Depends On:** Task 1

---

### Task 15: Remove SftpStepExecutor inline templating

**Files Touched:** `engine/executors/SftpStepExecutor.java`

**Definition of Done:**
- Replace the hardcoded `${fileName}`, `${fileExtension}`, `${timestamp}` replacement with generic ParamResolver usage
- Field definition for `remoteFileName` still accepts template strings; resolution happens at engine level now
- Existing behavior preserved: if user uses the old template syntax, it still works (same pattern)

**Test to Add:** `SftpStepExecutorTest` — verify remote file naming still works with templated values

**Depends On:** Task 9

---

### Task 16: Integration test suite — DAG scenarios

**Files Touched:** new `engine/DagExecutionIntegrationTest.java`

**Definition of Done:**
- Diamond DAG test (A→B, A→C, B+C→D): B and C start concurrently, D waits for both
- Linear chain regression test: 4-step job executes in order
- ON_FAILURE edge test: cleanup step runs only when upstream fails
- ALWAYS edge test: notification step runs regardless of upstream result
- Parameter templating end-to-end: POST with parameters, verify resolved values reach executor

**Test to Add:** All scenarios listed above as @TestMethodOrder integration tests with an in-memory H2 database (or Oracle test container)

**Depends On:** Task 12

---

### Effort Summary

| Parallel Track | Tasks | Estimated Days |
|----------------|-------|---------------|
| Database + API (Track A) | 1, 2, 4, 5, 14 | 5-6 days |
| Engine Core (Track B) | 3, 6, 7, 8, 9, 10, 11, 13 | 8-10 days |
| Integration + Cleanup (Track C) | 12, 15, 16 | 4-5 days |

**Critical path:** Track B → Task 12 → Track C = ~14-16 calendar days with parallel developers.
