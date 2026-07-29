<!-- FILE: phase3-06-testing-plan.md -->
# Phase 3.6 — Testing Plan

## Unit Tests

### DAGGraph (new class)
| Test | Input | Expected |
|------|-------|----------|
| Build from flat dependency list | 4 steps, edges: A→B, A→C, B→D, C→D | Graph has 4 nodes, 4 edges. Topological order contains all 4. |
| Cycle detection — simple cycle | A→B→A | `CycleDetectedException` with path [A, B, A] |
| Cycle detection — long cycle | A→B→C→D→A | `CycleDetectedException` with path containing all 4 nodes |
| Self-referencing edge | A→A | Validation error: "step cannot depend on itself" |
| Orphan node handling | Steps [A, B], edges: none | Both are root nodes. No validation error. |
| Diamond DAG topological sort | A→B, A→C, B→D, C→D | Valid order where A < {B,C} < D. B and C can be in either order relative to each other. |
| Single step, no edges | Step [A] | A is a root node. Topological sort returns [A]. |

### ParamResolver (new class)
| Test | Template | Context | Expected Output |
|------|----------|---------|-----------------|
| Job parameter resolution | `${job.param.API_URL}` | `{ "API_URL": "https://api.example.com" }` | `https://api.example.com` |
| Step output resolution | `${step.build.output.artifactPath}` | `{ build: { artifactPath: "/dist/app.jar" } }` | `/dist/app.jar` |
| Environment variable resolution | `${env.HOME}/work` | System env HOME=/home/user | `/home/user/work` |
| Multiple references in one string | `cp ${step.build.output.artifact} ${job.param.deployDir}` | Both resolved | `cp /dist/app.jar /opt/deploy` |
| Unknown job parameter (strict) | `${job.param.MISSING}` | Empty params map | Throws `TemplateResolutionException` |
| Unknown step output key | `${step.A.output.nonexistent}` | Step A outputs `{ "result": 42 }` | Throws `TemplateResolutionException` |
| Default value syntax | `${job.param.PORT?8080}` | Missing PORT | `8080` |
| Nested/recursive reference | `${job.param.OUTER_${job.param.INNER}}` | Not supported — reject at parse time | Parse error: "nested template references not supported" |
| No template markers | `plain-text-value` | Any context | `plain-text-value` (unchanged) |
| Escaped template | `\${job.param.NOT_A_TEMPLATE}` | Any context | `${job.param.NOT_A_TEMPLATE}` (literal) |

### DAGValidator (new class)
| Test | Input | Expected |
|------|-------|----------|
| Valid linear chain | A→B→C, all ON_SUCCESS | Passes validation. No cycles, no dangling references. |
| Reference to non-existent step in edge | Edge targets stepId "Z" which doesn't exist | Validation error: "dependency references unknown step 'Z'" |
| Template creates implicit cycle | Edges: A→B. Step C has template `${step.B.output.x}` and B has template `${step.C.output.y}` | Cycle detected in combined explicit+implicit graph |
| Duplicate edges | Two edges from A to B (different conditions) | Either reject or merge (document behavior). Recommend: reject with "duplicate edge A→B" |

### DAGStepScheduler (new class)
| Test | Input | Expected |
|------|-------|----------|
| Linear chain scheduling | A→B→C | Schedule order: [A], then [B] after A completes, then [C] after B completes |
| Diamond — concurrent execution | A→B, A→C, B→D, C→D | A runs first. B and C run concurrently (verified by timing). D runs after both complete. |
| Fan-out from single step | A→B, A→C, A→D | B, C, D all become runnable after A completes |
| Fan-in to single step | A→D, B→D, C→D | D waits for A, B, and C. Runs only when all three complete successfully. |
| Mixed edge conditions | A→B (ON_SUCCESS), A→C (ON_FAILURE) | If A succeeds: B runnable, C skipped. If A fails: C runnable, B skipped. |

## Integration Tests

### Diamond DAG Test (Core Concurrency Verification)
```
Job definition:
  Step A (JavaExec, ~100ms) → produces output { "value_A": "done" }
  Step B (ShellExec, ~200ms) → depends on A (ON_SUCCESS), reads ${step.A.output.value_A}
  Step C (ShellExec, ~200ms) → depends on A (ON_SUCCESS), reads ${step.A.output.value_A}
  Step D (JavaExec, ~100ms) → depends on B + C (ON_SUCCESS), reads both outputs

Assertions:
  1. All 4 steps complete SUCCESS
  2. Start time of B ≈ start time of C (within 50ms tolerance — proves concurrency)
  3. Start time of D > end time of B AND end time of C (D waited for both)
  4. Total execution time < sum of individual times (proves parallelism saved time)
  5. StepRunStep records show correct status and outputs for all 4 steps
```

### Conditional Edge Propagation Test
```
Job definition:
  Step A (always fails) → ON_SUCCESS edge to B, ON_FAILURE edge to C
  Step B (should not run) → ON_SUCCESS edge to D
  Step C (should run on failure) → ON_SUCCESS edge to D
  Step D (runs if either B or C succeeds)

Assertions:
  1. A status = FAILED
  2. B status = SKIPPED (ON_SUCCESS condition not met)
  3. C status = SUCCESS (ON_FAILURE condition met)
  4. D status = SUCCESS (ran after C succeeded)
```

### Cascading Skip Test
```
Job definition:
  A → B → C → D (all ON_SUCCESS edges)
  Step B is configured to fail

Assertions:
  1. A = SUCCESS
  2. B = FAILED
  3. C = SKIPPED (upstream B failed, no ON_FAILURE edge)
  4. D = SKIPPED (cascaded from C being skipped)
```

### Template Resolution End-to-End Test
```
Job definition with 3 steps:
  Step A produces output { "filename": "report.pdf" }
  Step B receives parameter inputPath="${step.A.output.filename}"
  Step C receives parameter reportUrl="${job.param.baseUrl}/${step.B.output.urlPath}"
  Launch parameters: { "baseUrl": "https://cdn.example.com" }

Assertions:
  1. Step B's executor receives inputPath="report.pdf"
  2. Step C's executor receives the fully resolved URL
  3. All intermediate template references are resolved correctly
```

### Backward Compatibility Regression Test
```
Use a backfilled linear job (V9 migration converted stepOrder to ALWAYS edges):
  Step 1 (ENV_SETUP) → Step 2 (ShellExec) → Step 3 (JavaExec)
  Original continueOnFailure=Y on Step 2

Assertions (must match pre-migration behavior):
  1. Steps execute in order 1, 2, 3
  2. If step 2 fails, step 3 still runs (continueOnFailure=Y → ALWAYS edge)
  3. ENV_SETUP outputs are available to downstream steps via upstreamOutputs
  4. Execution time is similar to sequential mode (no concurrency benefit for linear chain, but no regression either)
```

### Cancellation During Concurrent Execution Test
```
Job definition:
  Step A → Step B (~5s), Step C (~5s), Step D (~1s)
  Cancel the run after step A completes (while B and C are running)

Assertions:
  1. B and C are interrupted (subprocess killed, thread unblocked)
  2. D is marked SKIPPED or CANCELLED (never started)
  3. Run status = FAILED or CANCELLED
  4. No latch deadlock — the test completes within a reasonable timeout
```

## Regression Checklist for Backfilled Linear Jobs

- [ ] Linear job with `continueOnFailure=N` on step N: steps after N are skipped when N fails
- [ ] Linear job with `continueOnFailure=Y` on step N: steps after N run regardless of N's status
- [ ] ENV_SETUP → ShellExec chain: environment variables flow correctly to downstream step
- [ ] JavaExec step outputs are accessible via `${step.<id>.output.*}` in subsequent steps
- [ ] Retry policy still applies (retries on transient failure before marking FAILED)
- [ ] Audit log entries capture DAG-specific events (cycle detection rejection, template resolution errors)
- [ ] Cancel endpoint still works: POST to `/api/jobs/{id}/run` → cancel → run stops cleanly
- [ ] Live log streaming still delivers all step logs without loss under concurrent execution

## Test Infrastructure

| Need | Solution |
|------|----------|
| Fast test executors for timing verification | Create `SleepStepExecutor` (step type `SLEEP`, sleeps N ms, returns SUCCESS) — useful for concurrency timing tests without real I/O |
| Controllable failure executor | Create `FailingStepExecutor` (step type `FAIL`, always returns FAILED with configurable message) |
| Database testing | Use H2 in-memory with Flyway migrations for integration tests. Oracle-specific DDL only in migration files, not in test assertions. |
| Concurrency timing tolerance | Use 50-100ms window for "concurrent start" assertion — CI servers may have scheduling jitter. |
