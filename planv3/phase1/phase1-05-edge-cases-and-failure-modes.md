# Phase 1 — Edge Cases & Failure Modes

## Verification Status

**All 10 scenarios addressed in code.** Verified against actual implementation:

| Scenario | Handled In | Mechanism |
|----------|-----------|-----------|
| 1. Duplicate type registration | `StepExecutorRegistry.java` constructor | `putIfAbsent()` + warning log, last-registered wins |
| 2. Unknown step type at execution | `JobExecutionOrchestrator.executeStep()` line ~130 | `registry.get(type).orElseThrow()` with descriptive error message |
| 3. Plugin JAR fails to load | `PluginScanner.onApplicationEvent()` | Try-catch per JAR, logs error, continues scanning remaining JARs |
| 4. Concurrent registry access | `StepExecutorRegistry` field type | `ConcurrentHashMap` — thread-safe without external synchronization |
| 5. Config validation failure | `JobExecutionOrchestrator.validateRequiredFields()` lines 245–274 | Returns `StepResult.failure()` with missing field names, executor never invoked |
| 6. Missing credential reference | `SftpStepExecutor` via `CredentialResolver` | `resolver.resolve(ref)` throws → caught by orchestrator retry loop → FAILED result |
| 7. Step cancellation during execution | `StepContext.cancelRequested`, executor interrupt checks | Virtual thread in orchestrator checks `Thread.interrupted()` between retries; executors check `cancelRequested` flag |
| 8. Empty plugins directory | `PluginScanner.onApplicationEvent()` | Directory not found or empty → logs info message, continues startup |
| 9. Config JSON parse failure | Each executor's config parsing | Caught as runtime exception → orchestrator wraps in FAILED result with error message |
| 10. Retry exhaustion | `JobExecutionOrchestrator` retry loop lines 156–180 | After `maxAttempts`, returns last failed `StepResult` with cumulative execution time |

## Detailed Scenarios

### Scenario 1 — Duplicate Type Registration

**Trigger:** Two Spring beans or plugin JARs register an executor with the same `getType()` string.

**Behavior:** `StepExecutorRegistry` constructor calls `putIfAbsent(type, executor)`. If a value already exists for that type key, the existing one is kept and a warning is logged: `"Duplicate executor registration for type: {type}"`.

**Risk:** Low — Spring bean names are unique within a context; plugin conflicts would require two JARs with the same type string. Warning log provides visibility.

### Scenario 2 — Unknown Step Type at Execution Time

**Trigger:** A `JOB_STEP` row has `STEP_TYPE='CUSTOM_TYPE'` but no executor is registered for that type (plugin JAR missing, not deployed, or type typo).

**Behavior:** `registry.get("CUSTOM_TYPE")` returns `Optional.empty()`. Orchestrator throws a descriptive error: `"No executor registered for step type: CUSTOM_TYPE"`. Step marked as FAILED. If `continueOnFailure=true`, subsequent steps still execute.

**Risk:** Medium — silent data corruption if old enum values are dropped from the codebase without updating persisted rows. Mitigated by retaining all legacy executors.

### Scenario 3 — Plugin JAR Load Failure

**Trigger:** Corrupt JAR, missing dependency class, or incompatible `StepExecutor` implementation in a plugin JAR.

**Behavior:** `PluginScanner` wraps each JAR's loading in try-catch. On failure: logs error with JAR filename and exception message. Scanning continues for remaining JARs. Application starts successfully — only the failed plugin's executors are unavailable.

**Risk:** Low per-JAR isolation prevents one bad JAR from blocking others or crashing startup. However, a job referencing a missing plugin type will fail at execution time (Scenario 2).

### Scenario 4 — Concurrent Registry Access

**Trigger:** Multiple virtual threads executing steps concurrently, each calling `registry.get(type)`.

**Behavior:** `ConcurrentHashMap.get()` is thread-safe without synchronization. No performance degradation from locking. Registration happens only at startup (single-threaded), so no concurrent write concern during normal operation.

**Risk:** None — ConcurrentHashMap handles this correctly by design.

### Scenario 5 — Config Validation Failure

**Trigger:** Step config JSON is missing a required field defined in the executor's schema. E.g., SFTP step with `{"username": "admin"}` but no `"host"` field.

**Behavior:** Orchestrator parses config as `Map<String, Object>`, iterates schema fields where `required=true`, checks presence and non-blank. Missing fields collected into a list. Returns `StepResult.failure("Missing required config field(s): host", Duration.ZERO)` — executor's `execute()` is never called.

**Risk:** Low — validation happens before any I/O, fails fast with clear error message. Note: this is **presence-only** — it does not validate types, enum membership, or format. An SFTP step with `"host": "not-a-number"` for port would pass pre-validation but fail inside the executor.

### Scenario 6 — Missing Credential Reference

**Trigger:** SFTP step references `credentialRef: "prod-sftp-key"` but no row exists in `JOB_CREDENTIAL` with that ref.

**Behavior:** Orchestrator's `CredentialResolver` lambda calls `credentialRepo.findByCredentialRef(ref).orElseThrow(() -> new CredentialNotFoundException(ref))`. Exception propagates through executor → caught by orchestrator retry loop → after exhausting retries, step marked FAILED.

**Risk:** Medium — credential references are validated at execution time only, not at job save time. A typo in `credentialRef` won't be caught until the job runs. Future enhancement: validate credential refs at job definition save time.

### Scenario 7 — Step Cancellation During Execution

**Trigger:** User cancels a running job via API while a step is executing (e.g., long-running SFTP transfer or Java process).

**Behavior:**
1. `JobLaunchService.cancelRun(runId)` sets `cancelRequested` AtomicBoolean to true
2. Orchestrator's retry loop checks `Thread.interrupted()` between attempts — if interrupted, breaks out of retry and marks step FAILED
3. Long-running executors (JavaExec, ShellExec) check `context.getCancelRequested().get()` during execution and can abort early
4. JavaExecStepExecutor kills the child process on cancellation

**Risk:** Low — cancellation is cooperative; a blocking executor that doesn't check the flag will run to completion. All implemented executors check for cancellation.

### Scenario 8 — Empty or Missing Plugins Directory

**Trigger:** `orchestrator.plugins.dir` points to a non-existent directory, is empty, or is not set.

**Behavior:** If property is blank (default), scanning is skipped entirely with an info log. If directory doesn't exist, logs warning and skips. If directory exists but contains no JARs, logs info message. Application starts normally in all cases.

**Risk:** None — plugin loading is optional; the 8 built-in executors are on the classpath regardless.

### Scenario 9 — Config JSON Parse Failure

**Trigger:** `STEP_CONFIG` contains malformed JSON (e.g., trailing comma, unescaped quote).

**Behavior:** Each executor parses config with Jackson's `ObjectMapper`. A `JsonProcessingException` propagates up to the orchestrator's retry loop → caught as a runtime exception → step marked FAILED with error message containing the parse failure detail.

**Risk:** Low — malformed JSON fails fast with a clear error. However, this happens inside the executor rather than in pre-execute validation because the schema is descriptive-only (not used for parsing). Future enhancement: validate JSON structure against schema before invoking executor.

### Scenario 10 — Retry Exhaustion

**Trigger:** Executor's `defaultRetryPolicy()` allows N attempts; all N fail.

**Behavior:** Orchestrator's retry loop runs up to `maxAttempts` times with `delayBetweenAttempts` sleep between each. After exhausting retries, returns the last failed `StepResult`. Execution time reflects cumulative duration across all attempts (measured from first attempt start to final failure). If `continueOnFailure=true`, subsequent steps still execute; otherwise job fails at this step.

**Risk:** Low — retry behavior is executor-configured and visible in logs. No infinite retry loops possible because `maxAttempts` is bounded.

## Additional Edge Cases Observed in Code

### CredentialDecryptionService Key Padding

The encryption service pads keys shorter than 32 bytes with zeros (`CredentialDecryptionService.java:31-32`). This means a short key like `"mykey"` becomes `"mykey\0\0\0..."` — weak effective entropy. **Recommendation for production:** enforce minimum key length and use a proper key derivation function (PBKDF2/HKDF) rather than zero-padding.

### Default Encryption Key

The service has a hardcoded default key (`"default-encryption-key-32bytes!!"`). This means credentials are encrypted with a known key if `ORCHESTRATOR_ENCRYPTION_KEY` is not set. **Recommendation for production:** fail startup if the env var is not set, rather than falling back to a default.

### StepType Enum Incompleteness

The `StepType` enum contains only 5 values (ENV_SETUP, LOG_CLEANUP, JAVA_EXEC, SFTP, ARCHIVE) but does NOT include HTTP_CALL, SHELL_EXEC, or DB_QUERY. This is intentional — those types are not in the legacy enum because they were added as new types after opening the system. However, it means `getStepTypeEnum()` returns null for these valid types, which could confuse callers that expect a non-null enum value.
