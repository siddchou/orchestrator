# Phase 3.2 — Task Breakdown (Fix & Improve)

Phase 3 is ~90% implemented. These tasks address the remaining gaps identified in the code review. Each task is PR-sized.

---

### Task 1: Fix BUG-1 — Wire upstreamOutputs to StepContext

**Files Touched:** `engine/DagExecutionEngine.java` (line 626 area)

**Current Code:**
```java
.upstreamOutputs(Map.of())  // hardcoded empty map
```

**Fix:** Build `stepOutputs` from `completedResults` ConcurrentHashMap. For the current step's upstream dependencies, extract each completed StepResult's outputs map and populate the resolution context.

**Definition of Done:**
- `${step.<id>.output.X}` templates resolve to actual upstream output values
- Only upstream dependency outputs are exposed (not all completed steps)
- Unit test: create a 2-step job where step B has a parameter referencing step A's output; verify the executor receives the resolved value

**Depends On:** Nothing

---

### Task 2: Fix BUG-2 — Use SKIPPED status for skipped steps

**Files Touched:** `engine/DagExecutionEngine.java` (line 378 area), possibly `engine/spi/StepResult.java`

**Current Code:**
```java
StepResult.failure("Skipped - upstream condition not met")  // sets FAILED status
```

**Fix:** Add `StepResult.skipped(String message)` factory method if it doesn't exist. Use `StepStatus.SKIPPED` in the skip path of `signalDependents()`.

**Definition of Done:**
- Skipped steps persist with SKIPPED status in JOB_RUN_STEP
- Run detail UI displays SKIPPED correctly (already styled — see dark mode DAG canvas work)
- ON_FAILURE edges do NOT fire for a skipped upstream step
- Unit test: step A fails → step B (ON_SUCCESS dep on A) is SKIPPED, not FAILED

**Depends On:** Nothing

---

### Task 3: Fix BUG-3 — Narrow timing window

**Files Touched:** `engine/DagExecutionEngine.java` (lines 286-287 area)

**Fix:** Move `startTime = System.currentTimeMillis()` to immediately before the executor's `execute()` call. Move end-time capture to immediately after. Exclude template resolution and dependency signaling from the measured window.

**Definition of Done:**
- Step execution time in JOB_RUN_STEP reflects actual executor runtime
- Difference from current measurement is <50ms for steps that take >1s (acceptable)
- No functional change — metric accuracy only

**Depends On:** Nothing

---

### Task 4: Fix BUG-4 — Add CANCELLED status handling

**Files Touched:** `engine/spi/StepStatus.java` (add enum value), `engine/DagExecutionEngine.java` (cancel check path)

**Fix:**
1. Add `CANCELLED` to `StepStatus` enum
2. In the step task, check `cancelRequested` before executing; if true, mark CANCELLED and exit
3. In the cancel handler, mark pending steps as CANCELLED instead of FAILED

**Definition of Done:**
- Cancelled runs show CANCELLED status on pending steps (not FAILED)
- Completed steps retain their original result (SUCCESS/FAILED) when run is cancelled mid-execution
- Run-level status reflects cancellation appropriately
- Unit test: cancel a 3-step run after step 1 completes; verify step 1 = SUCCESS, steps 2-3 = CANCELLED

**Depends On:** Nothing

---

### Task 5: Verify envVars thread safety under concurrency

**Files Touched:** `engine/DagExecutionEngine.java` (StepContext builder), possibly `engine/spi/StepContext.java`

**Concern:** `StepContext.envVars` is a mutable HashMap. If two concurrent steps share the same ExecutionContext, ENV_SETUP mutations in one step could be visible to the other.

**Fix:** Ensure each step gets its own copy of envVars when building StepContext. The base copy comes from ExecutionContext (immutable snapshot at run start). Propagate ENV_SETUP changes via StepResult.outputs → upstreamOutputs, not through shared mutable state.

**Definition of Done:**
- Code review confirms no shared mutable HashMap between concurrent steps
- Test: two concurrent ENV_SETUP steps set different env vars; verify no cross-contamination
- Document the data flow: ExecutionContext.envVars → per-step copy → StepResult.outputs → downstream upstreamOutputs

**Depends On:** Task 1 (related data flow)

---

### Task 6: Verify CredentialResolver thread safety

**Files Touched:** `engine/CredentialDecryptionService.java` (verify), possibly no changes needed

**Concern:** `javax.crypto.Cipher` is not thread-safe. If the decryption service reuses a Cipher instance across calls, concurrent credential resolution could corrupt cipher state.

**Definition of Done:**
- Read CredentialDecryptionService source code
- If Cipher is created per call: document and move on
- If Cipher is reused: wrap in `ThreadLocal<Cipher>` or create fresh per call
- No functional change if already safe — this is an audit task

**Depends On:** Nothing

---

### Task 7: Add e2e concurrency test — diamond DAG timing proof

**Files Touched:** New `engine/DagExecutionConcurrencyTest.java` or add to existing test file

**Test Design:**
```
Step A (SleepExecutor, 100ms) → root
Step B (SleepExecutor, 300ms) → depends on A
Step C (SleepExecutor, 300ms) → depends on A
Step D (SleepExecutor, 100ms) → depends on B + C
```

**Assertions:**
1. All 4 steps complete SUCCESS
2. `B.startTime ≈ C.startTime` (within 100ms — proves concurrency)
3. `D.startTime > B.endTime AND D.startTime > C.endTime` (D waited for both)
4. Total run time < 700ms (A + max(B,C) + D = 100 + 300 + 100 = 500ms expected; sequential would be 800ms)

**Definition of Done:**
- Test passes consistently on local machine
- Uses SleepStepExecutor or mock executors with configurable delay
- Timing tolerance accounts for CI scheduling jitter (100ms window)

**Depends On:** Task 1 (upstreamOutputs must work for D to reference B+C outputs)

---

### Task 8: Clean up legacy comments and stale references

**Files Touched:** `engine/DagExecutionEngine.java`, any files with "Phase 2" or "TODO: Phase 3" comments

**Definition of Done:**
- Remove comments that reference unimplemented features as if they're future work
- Update comments that describe the sequential model to reflect DAG execution
- Remove unused imports, dead code paths

**Depends On:** Tasks 1-4 (fix bugs first, then clean up)

---

## Effort Summary

| Parallel Track | Tasks | Estimated Time |
|----------------|-------|---------------|
| Bug fixes (Track A) | 1, 2, 3, 4 | ~5.5 hours |
| Thread safety audit (Track B) | 5, 6 | ~1.5 hours |
| Testing + cleanup (Track C) | 7, 8 | ~3.5 hours |

**Critical path:** Track A → Task 7 = ~8-10 calendar hours with one developer.

**Total: ~2 story points** (down from ~30 for greenfield implementation).
