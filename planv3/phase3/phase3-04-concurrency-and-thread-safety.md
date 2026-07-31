<!-- FILE: phase3-04-concurrency-and-thread-safety.md -->
# Phase 3.4 — Concurrency and Thread Safety

## What Breaks When Steps Run Concurrently

### 1. StepContext envVars Mutation

**Current state:** `StepContext.envVars` is a mutable `HashMap`. The ENV_SETUP executor mutates this map directly (sets Java home, classpath, environment variables). In the sequential model, mutations flow forward to the next step — this is intentional.

**Breaks under concurrency:** Two steps running concurrently would share the same HashMap instance if built from the same ExecutionContext. Thread A writes `envVars.put("X", "1")` while thread B reads `envVars.get("X")` — classic data race. No synchronization on HashMap means possible visibility issues and corrupted internal state.

**Fix:** Each step gets its own copy of envVars. The base envVars come from ExecutionContext (immutable snapshot at run start). If ENV_SETUP runs and mutates its local copy, downstream steps that depend on it receive the mutated values through `StepContext.upstreamOutputs` — not through shared mutable state. Specifically:
- ENV_SETUP should store its resolved env vars in `StepResult.outputs` (e.g., `{ "envVars": { "JAVA_HOME": "/path" } }`)
- Downstream steps' StepContext merges these outputs into their envVars at build time
- This makes the data flow explicit and thread-safe

### 2. Live Log Queue Writes from Multiple Threads

**Current state:** `BlockingQueue<String> liveLogQueue` is a `LinkedBlockingQueue` shared per run. Each step writes log lines via `StepContext.LogSink.log()`.

**Thread safety analysis:** `LinkedBlockingQueue.add()` is thread-safe (uses ReentrantLock internally). Concurrent adds from multiple steps are safe — no data corruption, no lost entries. However:
- **Ordering:** Log lines from different steps may interleave arbitrarily. This is acceptable since each line should be prefixed with step identification (runId, stepName) for SSE consumers to distinguish them.
- **Memory pressure:** The current `QUEUE_WARNING_THRESHOLD` check at 10,000 entries ([StepContext.java:94](src/main/java/com/novakai/orchestrator/engine/spi/StepContext.java:94)) is not atomic (size check + log are two operations). Under concurrent writes, multiple threads could log the warning simultaneously. **Fix:** Use an `AtomicInteger` counter with `incrementAndGet()` for threshold checking, or accept the harmless duplicate warnings.

**Verdict:** No code change needed for correctness. Consider adding step-identification prefixes to log lines if not already present.

### 3. CredentialResolver Thread Safety

**Current state:** The credential resolver is a lambda created in `JobExecutionOrchestrator.buildStepContext()` (line 216):
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
- `credentialRepo.findByCredentialRef()` — Spring Data JPA repository call. Each call creates a new EntityManager transaction. Thread-safe.
- `decryptionService.decrypt()` — Need to verify this service is stateless. If it uses an instance-level cipher, and Java's `javax.crypto.Cipher` is **not thread-safe**, concurrent decryption calls would corrupt internal cipher state.

**Fix:** Check `CredentialDecryptionService` implementation:
- If it creates a new Cipher per call: already safe.
- If it reuses a Cipher instance: wrap in `SynchronizedWebDriver` or use `ThreadLocal<Cipher>`.
- **Recommendation:** Create a fresh Cipher per decrypt call. The performance cost is negligible (key size is small, and credential resolution happens once per step, not per iteration).

### 4. Cancellation Semantics Across Concurrent Branches

**Current state:** Cancellation works by:
1. `Future.cancel(true)` — interrupts the thread running the job's execution Future
2. `ExecutionContext.cancelRequested = true` — volatile flag checked before each step

**Breaks under concurrency:** With DAG execution, there is no single "job thread" — steps run on different threads from the pool. `Future.cancel(true)` only interrupts one thread (the one that submitted first), not all step threads. The volatile flag approach still works but needs to be checked at the right points.

**Fix:**
- Each step task checks `cancelRequested` before execution AND after dependency latch release (a step waiting on a latch should periodically check cancellation)
- Instead of relying on `Thread.interrupted()`, use the `cancelRequested` flag as the primary mechanism
- Use an `AtomicBoolean` for `cancelRequested` instead of `volatile boolean` — same memory semantics but more idiomatic for this pattern
- When cancellation is detected, interrupt any running step processes (subprocess handles from ShellExec/JavaExec) and mark remaining pending steps as CANCELLED

### 5. JobRunStep Repository Saves from Multiple Threads

**Current state:** `JobRunStepRepository.save()` is called after each step completes to persist the result. In sequential mode, saves are serialized.

**Breaks under concurrency:** Multiple threads calling `save()` concurrently for different `JobRunStep` entities (different `RUN_STEP_ID`) is safe — they're updating different rows. However:
- The `JobRun` entity's status field is updated in the finally block. If multiple step threads try to update run state simultaneously, there could be a race on the final status computation.

**Fix:**
- Step result persistence: safe as-is (different rows, no conflict)
- Run status update: done once by the DAG engine's main coordination logic after `runLatch.await()` — not by individual step threads
- Use `@Transactional` on the final status save to ensure consistency

### 6. Completed Results Map

**Current state:** No shared results map exists yet (upstreamOutputs is empty).

**DAG design:** `ConcurrentHashMap<String, StepResult> completedResults` — thread-safe by construction. Steps put their result here when done; downstream steps read from it after their dependency latch releases. Since StepResult is an immutable record, reads see a consistent snapshot without additional synchronization.

### Summary Table

| Component | Current State | Thread-Safe? | Fix Required |
|-----------|---------------|--------------|--------------|
| `StepContext.envVars` (HashMap) | Mutable, shared from ExecutionContext | No | Per-step copy; propagate via StepResult.outputs |
| `LiveLogQueue` (LinkedBlockingQueue) | Shared per run, thread-safe add() | Yes | Add step-identification prefix to log lines |
| `CredentialDecryptionService` | Unknown cipher reuse pattern | Unknown | Verify stateless; use fresh Cipher per call |
| `cancelRequested` flag | volatile boolean on ExecutionContext | Yes (for visibility) | Change to AtomicBoolean; check after latch release |
| `Future.cancel(true)` | Interrupts single thread | No for DAG | Use cancelRequested flag + explicit process termination |
| `JobRunStepRepository.save()` | Different rows per step | Yes | Move run status update to post-latch coordination |
| `completedResults` (new) | N/A — will be ConcurrentHashMap | By design | None |
