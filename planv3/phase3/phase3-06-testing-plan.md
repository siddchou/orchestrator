# Phase 3.6 — Testing Plan (Gap Analysis)

## Existing Tests

| Test File | Coverage | Status |
|-----------|----------|--------|
| `DagExecutionEngineTest.java` | DAG building, cycle detection, basic execution paths | Exists |
| `ParamResolverTest.java` | Template resolution for all three reference types, default values | Exists |
| `JobStepDependencyRepositoryTest.java` | CRUD on dependency entity, FK queries | May exist — verify |

## Test Gaps

### Gap 1: Concurrency timing proof (High priority)

**What's missing:** No test proves that independent branches actually execute concurrently. The existing tests may use mock executors that return instantly, which doesn't exercise the concurrency path.

**Test to add:** Diamond DAG with SleepExecutor (300ms delay per step):
```
A (100ms) → B (300ms), C (300ms) → D (100ms)
```

**Assertions:**
1. All 4 steps complete SUCCESS
2. `B.startTime ≈ C.startTime` (within 100ms tolerance — proves concurrency)
3. `D.startTime > B.endTime AND D.startTime > C.endTime`
4. Total run time < 700ms (sequential would be 800ms)

**Depends on:** BUG-1 fix (upstreamOutputs must work for D to reference B+C outputs)

### Gap 2: SKIPPED status test (High priority)

**What's missing:** No test verifies that skipped steps use `StepStatus.SKIPPED` instead of `FAILED`.

**Test to add:**
```
A (fails) → B (ON_SUCCESS dep on A) → C (ON_SUCCESS dep on B)
```
Assert: A = FAILED, B = SKIPPED, C = SKIPPED. Verify ON_FAILURE edges do NOT fire for skipped steps.

**Depends on:** BUG-2 fix

### Gap 3: Edge condition mixing test (Medium priority)

**What's missing:** No test verifies mixed edge conditions on the same downstream step.

**Test to add:**
```
A (fails) → B (ON_SUCCESS), C (ON_FAILURE), D (ALWAYS)
```
Assert: A = FAILED, B = SKIPPED, C = SUCCESS (or ran), D = SUCCESS (or ran).

### Gap 4: Cancel during concurrent execution (Medium priority)

**What's missing:** No test verifies cancellation behavior when steps are running concurrently.

**Test to add:**
```
A → B (~5s), C (~5s), D (~1s)
Cancel after A completes, while B and C are running.
```
Assert: B interrupted, C interrupted, D = CANCELLED (never started). No latch deadlock.

**Depends on:** BUG-4 fix

### Gap 5: Parameter threading end-to-end (Medium priority)

**What's missing:** No integration test that traces parameters from POST body → launch service → ExecutionContext → DagExecutionEngine → ParamResolver → StepContext → executor.

**Test to add:**
```
POST /api/jobs/{id}/run with { "parameters": { "env": "staging" } }
Step A has config: { "targetEnv": "${job.param.env}" }
Assert executor receives: { "targetEnv": "staging" }
```

### Gap 6: Cross-step output templating (High priority)

**What's missing:** No test verifies `${step.<id>.output.X}` resolution works end-to-end. Currently broken by BUG-1.

**Test to add:**
```
Step A produces output { "filename": "report.pdf" }
Step B has config: { "inputPath": "${step.A.output.filename}" }
Assert Step B's executor receives: { "inputPath": "report.pdf" }
```

**Depends on:** BUG-1 fix

## Regression Checklist for Backfilled Linear Jobs

- [ ] Linear job with `continueOnFailure=N` on step N: steps after N are skipped when N fails
- [ ] Linear job with `continueOnFailure=Y` on step N: steps after N run regardless of N's status (ALWAYS edge)
- [ ] ENV_SETUP → ShellExec chain: environment variables flow correctly to downstream step
- [ ] JavaExec step outputs are accessible via `${step.<id>.output.*}` in subsequent steps
- [ ] Retry policy still applies (retries on transient failure before marking FAILED)
- [ ] Cancel endpoint still works: run stops cleanly, pending steps marked CANCELLED
- [ ] Live log streaming delivers all step logs without loss under concurrent execution

## Test Infrastructure Needs

| Need | Solution |
|------|----------|
| Fast test executors for timing verification | Create `SleepStepExecutor` (step type `SLEEP`, sleeps N ms, returns SUCCESS) |
| Controllable failure executor | Create `FailingStepExecutor` (step type `FAIL`, always returns FAILED) |
| Database testing | Use H2 in-memory with Flyway migrations for integration tests |
| Concurrency timing tolerance | Use 50-100ms window for "concurrent start" assertion — CI servers may have scheduling jitter |

## UI Testing

The DAG canvas (`RunDagCanvasComponent`) has its own test coverage (18/20 tasks complete per IMPLEMENTATION_STATUS.md). E2e tests are blocked by missing e2e framework — out of scope for Phase 3 backend fixes.
