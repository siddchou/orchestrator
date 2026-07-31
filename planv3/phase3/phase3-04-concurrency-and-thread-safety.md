# Phase 3.4 — Concurrency and Thread Safety (Audit)

## Components Under Concurrent Load

When the DAG engine runs independent branches in parallel, these components are accessed from multiple threads simultaneously:

### 1. StepContext.envVars — Data Race Risk

**Current state:** `StepContext.envVars` is a mutable `HashMap`. The ENV_SETUP executor mutates this map directly (sets Java home, classpath, environment variables). In the sequential model, mutations flow forward to the next step — this is intentional.

**Under concurrency:** If two steps share envVars from the same ExecutionContext, Thread A's ENV_SETUP writes could be visible to Thread B mid-execution. No synchronization on HashMap means possible visibility issues and corrupted internal state.

**Fix (Task 5):** Each step gets its own copy of envVars at StepContext build time. Base envVars come from ExecutionContext (immutable snapshot). ENV_SETUP mutations stay local; downstream steps receive mutated values through `StepResult.outputs` → `upstreamOutputs`, not through shared mutable state.

### 2. Live Log Queue — Thread-Safe

**Current state:** `BlockingQueue<String> liveLogQueue` is a `LinkedBlockingQueue` shared per run. Each step writes log lines via `StepContext.LogSink.log()`.

**Thread safety analysis:** `LinkedBlockingQueue.add()` is thread-safe (uses ReentrantLock internally). Concurrent adds from multiple steps are safe — no data corruption, no lost entries.

**Caveats:**
- **Ordering:** Log lines from different steps may interleave arbitrarily. Acceptable if each line is prefixed with step identification (runId, stepName).
- **Queue warning threshold:** The `QUEUE_WARNING_THRESHOLD` check at 10,000 entries is not atomic (size check + log are two operations). Under concurrent writes, multiple threads could log the warning simultaneously. Harmless — no fix needed unless noise is a concern.

**Verdict:** No code change needed for correctness.

### 3. CredentialResolver — Unknown Risk

**Current state:** The credential resolver is a lambda created in `JobExecutionOrchestrator.buildStepContext()`:
```java
StepContext.CredentialResolver credentialResolver = ref -> {
    var cred = credentialRepo.findByCredentialRef(ref)
        .orElseThrow(() -> new RuntimeException("Credential not found: " + ref));
    try {
        return decryptionService.decrypt(cred.getCredValue());
    } catch (Exception e) {
        throw new RuntimeException("Failed to decrypt credential: " + ref, e);
    }
};
```

**Thread safety analysis:**
- `credentialRepo.findByCredentialRef()` — Spring Data JPA, each call creates a new EntityManager transaction. Thread-safe.
- `decryptionService.decrypt()` — **Unknown.** If it reuses a `javax.crypto.Cipher` instance, and Java's Cipher is **not thread-safe**, concurrent decryption calls would corrupt internal cipher state.

**Fix (Task 6):** Read `CredentialDecryptionService` source. If Cipher is reused, create fresh per call or use `ThreadLocal<Cipher>`. Performance cost is negligible — credential resolution happens once per step, not per iteration.

### 4. Cancellation Semantics — Partial Fix Needed

**Current state:**
1. `Future.cancel(true)` — interrupts the thread running the job's execution Future
2. `ExecutionContext.cancelRequested = true` — volatile flag checked before each step

**Under concurrency:** With DAG execution, there is no single "job thread" — steps run on different threads from the pool. `Future.cancel(true)` only interrupts one thread (the one that submitted first), not all step threads. The volatile flag approach still works but needs to be checked at more points.

**Fix (Task 4):**
- Each step task checks `cancelRequested` before execution AND after dependency latch release
- Use the `cancelRequested` flag as the primary mechanism (not `Thread.interrupted()`)
- When cancellation is detected, mark pending steps as CANCELLED (not FAILED)
- Interrupt any running step processes (subprocess handles from ShellExec/JavaExec)

### 5. JobRunStep Repository Saves — Thread-Safe

**Current state:** `JobRunStepRepository.save()` is called after each step completes to persist the result. In sequential mode, saves are serialized.

**Under concurrency:** Multiple threads calling `save()` concurrently for different `JobRunStep` entities (different `RUN_STEP_ID`) is safe — they're updating different rows. No row-level conflict.

**Caveat:** The `JobRun` entity's status field should be updated once by the DAG engine's coordination logic after `runLatch.await()` — not by individual step threads. Verify this is the case in the current code.

### 6. Completed Results Map — Thread-Safe by Design

`ConcurrentHashMap<String, StepResult> completedResults` — thread-safe by construction. Steps put their result here when done; downstream steps read from it after their dependency latch releases. Since StepResult is an immutable record, reads see a consistent snapshot without additional synchronization.

## Summary Table

| Component | Thread-Safe? | Fix Required | Task |
|-----------|--------------|--------------|------|
| `StepContext.envVars` (HashMap) | No — shared mutable state | Per-step copy; propagate via StepResult.outputs | Task 5 |
| `LiveLogQueue` (LinkedBlockingQueue) | Yes — internal ReentrantLock | None | — |
| `CredentialDecryptionService` | Unknown — cipher reuse pattern TBD | Verify stateless; fresh Cipher per call if needed | Task 6 |
| `cancelRequested` flag | Yes for visibility, but incomplete coverage | Check after latch release; use CANCELLED status | Task 4 |
| `Future.cancel(true)` | No for DAG — only interrupts one thread | Use cancelRequested flag + explicit process termination | Task 4 |
| `JobRunStepRepository.save()` | Yes — different rows per step | Ensure run status update is post-latch, not per-step | Verify |
| `completedResults` (ConcurrentHashMap) | Yes — by design | None | — |

## Concurrency Primitives Used

| Primitive | Purpose | Correct? |
|-----------|---------|----------|
| `Semaphore(5)` | Bound concurrent step execution | Yes — acquire before execute, release in finally |
| `CountDownLatch(stepCount)` | Wait for all steps to complete | Yes — countdown in finally block |
| `ConcurrentHashMap` | Collect step results thread-safely | Yes — immutable StepResult values |
| `ThreadPoolTaskExecutor` (core=10, max=20, queue=50) | Execute step tasks | Yes — Spring-managed lifecycle |
| `volatile boolean cancelRequested` | Cross-thread cancellation signal | Partially — visibility is correct, but check points are incomplete |
