# Phase 3.5 — Edge Cases and Failure Modes (Implementation Audit)

## How the Current Code Handles Each Scenario

| Scenario | Current Handling | Correct? | Severity |
|----------|-----------------|----------|----------|
| **Circular dependency** (A→B→C→A) | `validateAcyclic()` uses Kahn's algorithm — if visited count != step count, throws `CircularDependencyException` | Yes — Kahn's detects all cycles including self-loops | OK |
| **Self-referencing edge** (A→A) | Kahn's algorithm: node A has in-degree 1 from itself; it can never become a root; visited count excludes A; exception thrown | Yes — self-loop is a cycle | OK |
| **Orphan step** (zero deps, zero dependents) | Treated as a root node — no dependencies means it's immediately runnable. No validation error. | Acceptable — the step runs concurrently with other roots. Could log an info message for visibility. | Info only |
| **Template reference to non-existent step** `${step.nonexistent.output.result}` | ParamResolver regex matches but key lookup returns null → unresolved reference left as literal or replaced with default if syntax used | Partially — should reject at validation time, not silently leave unresolved. Low priority since the step will likely fail at execution time anyway. | P2 |
| **Template creates implicit cycle** | Not detected. The explicit dependency graph (JOB_STEP_DEPENDENCY) is validated for cycles, but template references create implicit dependencies that are not traced. If step A templates `${step.B.output.x}` and B depends on A explicitly, the explicit graph catches it. But if there's no explicit edge, the implicit cycle could cause a runtime null (B hasn't run when A needs its output). | Gap — but mitigated by BUG-1 fix: once upstreamOutputs is properly wired, referencing a non-upstream step's output simply returns null (step not yet in completedResults). The step would fail at execution time with a null parameter. | P2 |
| **Missing job parameter** `${job.param.API_KEY}` | ParamResolver returns null or default value if `?default` syntax used. No strict validation at launch time. | Acceptable for now — the executor fails with a clear error message when it receives a null/empty required parameter. Strict mode can be added later. | P2 |
| **Step output key doesn't exist** `${step.B.output.nonexistentKey}` | Same as above — null resolution, left as literal or default. | Acceptable — dynamic outputs are hard to validate at plan time. | P2 |
| **Step fails with ON_FAILURE edge** | `signalDependents()` checks edge condition against StepResult status. ON_FAILURE + FAILED upstream → downstream fires. | Implemented correctly in code. However, BUG-2 means SKIPPED steps are recorded as FAILED, which could incorrectly trigger ON_FAILURE edges. **Fixing BUG-2 resolves this.** | Fixed by BUG-2 |
| **Cascading skip** (A fails → B skipped → C skipped) | Each step independently evaluates its edge conditions against upstream results. If B is SKIPPED and C has ON_SUCCESS dep on B, C sees SKIPPED ≠ SUCCESS → C also skips. | Logic is correct in code. Depends on BUG-2 fix for correct status values. | Fixed by BUG-2 |
| **Step crashes without saving result** | `submitStep()` uses try-finally: finally block always puts a FAILED result in `completedResults` and counts down the latch. Downstream steps unblock even if executor throws. | Implemented correctly — liveness is guaranteed. | OK |
| **Per-step timeout** | Not implemented. A hung step blocks its downstream indefinitely (bounded only by run-level timeout on `runLatch.await()`). | Gap — a per-step timeout (configurable, default 30 min) would be a good backstop. Out of scope for Phase 3 bug fixes. | P2 — future enhancement |
| **Template injection** `${job.param.MALICIOUS}` | ParamResolver resolves values; the executor (ShellExec) is responsible for safe argument handling. | Documented as user responsibility: concurrent steps should not share mutable external resources. No orchestrator-level escaping. | Out of scope |

## Edge Cases Specific to Concurrent Execution

### Semaphore starvation
With `Semaphore(5)` and a DAG that has 6+ independent root steps, 5 run immediately and 1 waits. If the 5 running steps each have long downstream chains, the waiting root step is delayed. This is by design — the semaphore bounds DB load. Not a bug, but worth documenting for users who expect all roots to start simultaneously.

### Latch ordering
The `CountDownLatch` per run counts down once per step (in finally block). Order doesn't matter — it just needs to reach zero. Correct as implemented.

### ConcurrentHashMap size
The map holds one entry per step. For a 100-step job, that's 100 StepResult records in memory. Negligible memory footprint. No eviction needed since the DAG is finite and bounded by the job definition.

## Summary of Action Items from Edge Cases

| Item | Priority | Linked Task |
|------|----------|-------------|
| Fix SKIPPED-as-FAILED (affects cascading skip, ON_FAILURE edges) | High | BUG-2 / Task 2 |
| Add info-level log for orphan steps | Low | — |
| Consider strict mode for missing job parameters | Low | Future enhancement |
| Add per-step timeout backstop | Medium | Future enhancement |
| Validate template references against known step IDs at DAG build time | Low | Future enhancement |
