# Phase 1 — Interfaces & Data Model

## Design Principles (Grounded in Code Review Findings)

1. **Backward compatible in contract, not in interface type**: The current `StepExecutor` *contract* (a class per step type, dispatched via Spring auto-collection) is preserved and evolved. But `engine.spi.StepExecutor` is a **new interface in a new package** — a clean replacement of `com.novakai.orchestrator.engine.StepExecutor`, not an in-place edit of that file. Each existing executor is switched over to implement the new interface (one small PR per executor); the old interface file is left in place but unimplemented once migration completes. This keeps the migration a series of small, isolated PRs instead of one big-bang interface change. (See `phase1-07-gap-analysis-and-fixes.md`, Fix #6.)
2. **SSE-safe**: `StepContext` carries the same `BlockingQueue<String> liveLogQueue` that executors write to today — the SSE controller at `LogStreamController.java:30-86` reads from it unchanged.
3. **Open type system**: Replace `StepType` enum key with `String getType()`. The factory's map becomes `Map<String, StepExecutor>`. Existing enum values still work via `.name()` bridge.

---

## 1. FieldDefinition

```java
package com.novakai.orchestrator.engine.spi;

/**
 * Describes a single field in a step configuration schema.
 * Consumed by Phase 2's dynamic form generator and by config validation at runtime.
 */
public record FieldDefinition(
    String name,                    // JSON key: "url", "method", "credentialRef"
    String label,                   // UI display label: "URL", "HTTP Method"
    FieldType type,                 // see enum below
    boolean required,               // must be present in config JSON
    Object defaultValue,            // null if no default; used by form generator for initial value
    List<String> enumValues,        // non-null only when type == ENUM; drives <select> options
    String helpText                 // inline tooltip / description in UI
) {
    public FieldDefinition {
        if (type == FieldType.ENUM && (enumValues == null || enumValues.isEmpty())) {
            throw new IllegalArgumentException("ENUM fields must provide enumValues");
        }
        if (type != FieldType.ENUM && enumValues != null && !enumValues.isEmpty()) {
            throw new IllegalArgumentException("enumValues only valid for ENUM type");
        }
    }
}

public enum FieldType {
    STRING,       // free-form text input
    NUMBER,       // numeric input (int/long/double)
    BOOLEAN,      // checkbox / toggle
    ENUM,         // select dropdown; values from FieldDefinition.enumValues
    SECRET_REF,   // credential reference picker (resolves to JobCredential.credentialRef)
    FILE_PATTERN  // glob pattern input with validation hint
}
```

**Rationale**: The current codebase has no schema concept — each executor silently parses JSON into a typed record. `FieldDefinition` makes the config contract explicit, enabling Phase 2's form generator and runtime validation. `SECRET_REF` is its own type (not STRING) so the UI can render a credential-picker dropdown instead of a plaintext input — this matches how SFTP already uses `credentialRef`.

---

## 2. StepConfigSchema

```java
package com.novakai.orchestrator.engine.spi;

/**
 * Machine-readable schema for a step type's configuration fields.
 * Drives UI form generation (Phase 2) and runtime config validation.
 */
public record StepConfigSchema(
    String stepType,              // e.g. "HTTP_CALL" — matches StepExecutor.getType()
    String displayName,           // e.g. "HTTP Call" — shown in palette
    List<FieldDefinition> fields  // ordered list; UI renders fields in this order
) {}
```

**Rationale**: Simple data carrier. No behavior needed. The registry collects one schema per executor via `getConfigSchema()`. Phase 2's Angular form generator fetches the list from `GET /api/step-types` and builds reactive forms dynamically.

---

## 3. RetryPolicy

```java
package com.novakai.orchestrator.engine.spi;

import java.time.Duration;

/**
 * Declarative retry policy that an executor can declare as its default.
 * The orchestrator wraps execute() with this policy — executors don't implement retry themselves.
 */
public record RetryPolicy(
    int maxAttempts,              // 0 = no retry (execute once), 1+ = total attempts including first
    Duration delayBetweenAttempts // backoff interval; null means immediate retry
) {
    public static RetryPolicy none() {
        return new RetryPolicy(0, null);
    }

    public static RetryPolicy fixed(int attempts, Duration delay) {
        return new RetryPolicy(attempts - 1, delay); // attempts-1 retries after first attempt
    }

    /** Number of retries AFTER the initial attempt. */
    public int retries() {
        return Math.max(0, maxAttempts);
    }

    public boolean hasRetries() {
        return maxAttempts > 0;
    }
}
```

**Rationale**: Code review found **no retry logic exists anywhere**. JavaExecStepExecutor handles timeout internally (per-process `waitFor`). Making retry an orchestrator-level concern means every executor benefits without duplicating retry loops. The current per-executor timeout behavior is preserved: executors still declare their own timeout in config fields, and the orchestrator respects it by calling `execute()` which returns on timeout — then the retry policy decides whether to re-invoke.

---

## 4. StepResult (v2)

```java
package com.novakai.orchestrator.engine.spi;

import java.time.Duration;
import java.util.Map;

/**
 * Result of executing a single step. Replaces the old record
 * StepResult(boolean success, int exitCode, String logOutput).
 */
public record StepResult(
    StepStatus status,                    // SUCCESS / FAILED / SKIPPED
    Map<String, Object> outputs,          // structured outputs for Phase 3 templating
    String message,                       // human-readable summary (replaces old logOutput for metadata)
    Duration executionTime                // wall-clock time of this execute() call
) {
    public static StepResult success(Map<String, Object> outputs, String message, Duration time) {
        return new StepResult(StepStatus.SUCCESS, outputs, message, time);
    }

    public static StepResult failure(String message, Duration time) {
        return new StepResult(StepStatus.FAILED, Map.of(), message, time);
    }

    /** Backward compat: was the step successful? (maps to old boolean success field) */
    public boolean isSuccess() {
        return status == StepStatus.SUCCESS;
    }

    /** Backward compat: maps exit code from outputs if present, else -1 for failure. */
    public int getExitCode() {
        Object obj = outputs.get("exitCode");
        if (obj instanceof Number n) return n.intValue();
        return isSuccess() ? 0 : -1;
    }

    /** Backward compat: returns message as log output string for existing consumers. */
    public String getLogOutput() {
        return message != null ? message : "";
    }
}

public enum StepStatus {
    SUCCESS,
    FAILED,
    SKIPPED   // for Phase 3 conditional execution; not used in Phase 1 but reserved now
}
```

**Rationale**: The old `StepResult(boolean success, int exitCode, String logOutput)` is consumed by:
- `JobExecutionOrchestrator.executeStep()` — checks `result.success()`, reads `exitCode` and `logOutput` for DB persistence.
- Every executor's return statement.

The v2 record adds `outputs` (Map) and `executionTime` while providing backward-compat accessor methods (`isSuccess()`, `getExitCode()`, `getLogOutput()`). The orchestrator's existing calls to `result.success()` need one-line updates to `result.isSuccess()`. Executors return the new form via static factory methods.

---

## 5. StepContext (v2)

```java
package com.novakai.orchestrator.engine.spi;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Runtime context passed to every step executor. Replaces ExecutionContext
 * while preserving the fields that existing code depends on.
 */
public class StepContext {

    private final Long runId;
    private final String stepId;                     // logical step identifier (name or DB id as string)
    private final Map<String, Object> resolvedParams; // run-time parameters (Phase 3 templating input)
    private final CredentialResolver credentials;     // typed credential access (wraps decryption service)
    private final LogSink logSink;                    // abstraction over liveLogQueue for SSE compat
    private final Path workDir;
    private final Map<String, StepResult> upstreamOutputs; // completed sibling step results (Phase 3)

    /* --- backward-compat fields from ExecutionContext --- */
    private Long jobId;
    private String javaHome;
    private java.util.List<String> classpath;
    private Map<String, String> envVars;
    private volatile boolean cancelRequested;        // must remain volatile for interrupt check

    public StepContext(Builder builder) {
        this.runId = builder.runId;
        this.stepId = builder.stepId;
        this.resolvedParams = Map.copyOf(builder.resolvedParams);
        this.credentials = builder.credentials;
        this.logSink = builder.logSink;
        this.workDir = builder.workDir;
        this.upstreamOutputs = Map.copyOf(builder.upstreamOutputs);
        this.jobId = builder.jobId;
        this.javaHome = builder.javaHome;
        this.classpath = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(builder.classpath));
        this.envVars = new java.util.HashMap<>(builder.envVars); // mutable — ENV_SETUP mutates this
        this.cancelRequested = false;
    }

    // --- getters ---
    public Long getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public Map<String, Object> getResolvedParams() { return resolvedParams; }
    public CredentialResolver getCredentials() { return credentials; }
    public LogSink getLogSink() { return logSink; }
    public Path getWorkDir() { return workDir; }
    public Map<String, StepResult> getUpstreamOutputs() { return upstreamOutputs; }

    // backward compat with ExecutionContext API
    public Long getJobId() { return jobId; }
    public String getJavaHome() { return javaHome; }
    public void setJavaHome(String v) { this.javaHome = v; }
    public java.util.List<String> getClasspath() { return classpath; }
    public void setClasspath(java.util.List<String> v) { this.classpath = java.util.Collections.unmodifiableList(v); }
    public Map<String, String> getEnvVars() { return envVars; }
    public BlockingQueue<String> getLiveLogQueue() { return logSink.getQueue(); } // SSE compat bridge
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean v) { this.cancelRequested = v; }

    // --- CredentialResolver (functional interface) ---
    @FunctionalInterface
    public interface CredentialResolver {
        /** Resolve a credential reference to its decrypted value. Throws if not found. */
        String resolve(String credentialRef);
    }

    // --- LogSink (wraps BlockingQueue for SSE compatibility) ---
    public static class LogSink {
        private final BlockingQueue<String> queue;

        public LogSink(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        public void log(String line) {
            if (queue != null && !line.isBlank()) {
                queue.add(line);
            }
        }

        /** Exposes the raw queue for backward compat with existing executor code. */
        public BlockingQueue<String> getQueue() { return queue; }
    }

    // --- Builder ---
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long runId, jobId;
        private String stepId, javaHome;
        private Map<String, Object> resolvedParams = Map.of();
        private CredentialResolver credentials;
        private LogSink logSink;
        private Path workDir;
        private Map<String, StepResult> upstreamOutputs = Map.of();
        private java.util.List<String> classpath = new java.util.ArrayList<>();
        private Map<String, String> envVars = new java.util.HashMap<>();

        public Builder runId(Long v) { this.runId = v; return this; }
        public Builder jobId(Long v) { this.jobId = v; return this; }
        public Builder stepId(String v) { this.stepId = v; return this; }
        public Builder resolvedParams(Map<String, Object> v) { this.resolvedParams = v; return this; }
        public Builder credentials(CredentialResolver v) { this.credentials = v; return this; }
        public Builder logSink(LogSink v) { this.logSink = v; return this; }
        public Builder workDir(Path v) { this.workDir = v; return this; }
        public Builder upstreamOutputs(Map<String, StepResult> v) { this.upstreamOutputs = v; return this; }
        public Builder javaHome(String v) { this.javaHome = v; return this; }
        public Builder classpath(java.util.List<String> v) { this.classpath = new java.util.ArrayList<>(v); return this; }
        public Builder envVars(Map<String, String> v) { this.envVars = new java.util.HashMap<>(v); return this; }

        public StepContext build() { return new StepContext(this); }
    }
}
```

**Rationale — SSE compatibility**: The critical link is `getLiveLogQueue()` which returns the raw `BlockingQueue<String>`. Existing executor code does `ctx.getLiveLogQueue().add(line)` (e.g., `JavaExecStepExecutor.java:137`). This bridge method ensures that line continues to work without modification. New executors can use `logSink.log(line)` for null-safety, but the raw queue access remains for backward compat.

**Rationale — CredentialResolver**: Instead of injecting `JobCredentialRepository` + `CredentialDecryptionService` into every executor (only SFTP needs them today), provide a functional interface on StepContext. The orchestrator builds the resolver at run time: `ref -> decryptionService.decrypt(credentialRepo.findByCredentialRef(ref).orElseThrow(...).getCredValue())`. This keeps executors decoupled from persistence infrastructure.

**Rationale — envVars mutable**: `EnvSetupStepExecutor` mutates `ctx.getEnvVars().putAll(...)`. The map inside StepContext is a mutable copy (not the system env), matching current behavior where `JobLaunchService.buildEnvMap()` creates a new LinkedHashMap.

---

## 6. StepExecutor (v2 — extended)

```java
package com.novakai.orchestrator.engine.spi;

/**
 * Contract for a pluggable step type. Extends the existing StepExecutor interface
 * by adding schema and retry support, while keeping backward compat via default methods.
 */
public interface StepExecutor {

    /** Unique string identifier for this step type (e.g. "HTTP_CALL"). Replaces StepType enum key. */
    String getType();

    /** Machine-readable config schema — drives UI forms and runtime validation. */
    StepConfigSchema getConfigSchema();

    /** Execute the step. The orchestrator handles retry, timing, and result persistence. */
    StepResult execute(StepContext ctx) throws Exception;

    /** Default retry policy for this executor type. Override to enable retries. */
    default RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.none();
    }
}
```

**Rationale — String getType() vs StepType enum**: The old interface returned `StepType` (closed enum). Changing the return type to `String` opens the system: new types register without touching the enum. The existing executors simply return their current enum's `.name()` value (e.g., `return "JAVA_EXEC"` instead of `return StepType.JAVA_EXEC`). The factory map key changes from `StepType` to `String`.

**Rationale — execute(StepContext) vs execute(ExecutionContext, JobStep)**: The old signature passed both context and the JPA entity. The new design embeds step identity in context (`stepId`) instead of passing the entity directly.

**Phase 1 scope for config parsing (corrected — see gap-analysis Fix #3)**: `getConfigSchema()` in Phase 1 is a **descriptive contract only**. It tells the UI (Phase 2) and the `/api/step-types` endpoint what fields exist, their types, and which are required — it does not yet drive a shared parsing/resolution layer. Each executor continues to parse `step.getStepConfig()` (the JSON CLOB) into its own typed record exactly as it does today, via `jsonParser.parse(step.getStepConfig(), ConfigClass.class)`. This is precisely why the migration can be "byte-for-byte identical" per executor.

What the orchestrator *does* add in Phase 1 is a lightweight, separate check: before invoking `execute()`, it verifies that every field marked `required=true` in the schema is present and non-blank in the raw config JSON (presence-only, no type coercion or enum validation — see Task 9 in `phase1-02-task-breakdown.md`). Full schema-driven parsing/validation replacing each executor's own parsing logic is a natural Phase 2+ follow-up, once the UI is actually generating config from the schema and type mismatches become possible to introduce — pulling that forward into Phase 1 would add a second config-parsing layer that could drift from what executors actually expect, for no Phase 1 benefit.
