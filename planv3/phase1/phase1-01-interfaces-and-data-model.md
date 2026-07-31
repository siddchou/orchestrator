# Phase 1 — Interfaces & Data Model

## Design Principles (Grounded in Code Review Findings)

1. **Backward compatible in contract, not in interface type**: The current `StepExecutor` *contract* (a class per step type, dispatched via Spring auto-collection) is preserved and evolved. But `engine.spi.StepExecutor` is a **new interface in a new package** — a clean replacement of `com.novakai.orchestrator.engine.StepExecutor`, not an in-place edit of that file. Each existing executor is switched over to implement the new interface (one small PR per executor); the old interface file is left in place but unimplemented once migration completes.

2. **String-based type resolution**: `stepType` on `JobStep` is a plain `String` — no `@Enumerated`, no `AttributeConverter`. The enum is retained for backward-compat overloads only.

3. **Config schema is descriptive in Phase 1**: `getConfigSchema()` returns metadata about what fields an executor expects. The orchestrator uses this for presence-only validation of required fields before calling `execute()`. Full type/enum/format validation stays inside each executor.

4. **Orchestrator owns retry and timing**: Each executor declares a `defaultRetryPolicy()` but the retry loop, interrupt handling, and execution-time measurement live in the orchestrator — not duplicated per executor.

---

## SPI Interface Definitions

### StepExecutor

```java
package com.novakai.orchestrator.engine.spi;

public interface StepExecutor {
    String getType();
    StepConfigSchema getConfigSchema();
    StepResult execute(StepContext context);
    default RetryPolicy defaultRetryPolicy() { return RetryPolicy.none(); }
}
```

**Design rationale:**
- `getType()` returns a plain String (not enum) — enables open type registration
- `getConfigSchema()` is descriptive-only in Phase 1; consumed by orchestrator for pre-execute validation and by the `/api/step-types` endpoint for UI form generation (Phase 2)
- `execute(StepContext)` replaces the old varied signatures with a single context object
- `defaultRetryPolicy()` defaults to no retry — executors that benefit from retries override this

### StepConfigSchema + FieldDefinition

```java
package com.novakai.orchestrator.engine.spi;

public record StepConfigSchema(String stepType, String displayName, List<FieldDefinition> fields) {}

public record FieldDefinition(
    String name, String label, FieldType type, boolean required,
    String defaultValue, List<String> enumValues, String helpText
) {
    // Constructor validates: ENUM type must have non-null enumValues
}

public enum FieldType {
    STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING
}
```

**Design rationale:**
- `FieldType` covers the shapes needed across all 8 executors without over-engineering
- `SECRET_REF` signals that a field references an encrypted credential (not plaintext)
- `FILE_PATTERN` distinguishes glob patterns from plain file paths for UI hinting
- Constructor validation on ENUM ensures schema authors don't forget `enumValues`

### StepContext

```java
package com.novakai.orchestrator.engine.spi;

public class StepContext {
    // Immutable fields set via builder:
    Long runId, stepId;
    String stepConfig;              // raw JSON from JOB_STEP.STEP_CONFIG
    Map<String, Object> resolvedParams;  // parameter-resolved config
    CredentialResolver credentials;  // functional interface: String ref -> String decryptedValue
    LogSink logSink;                // wraps BlockingQueue<String> for SSE streaming
    Path workDir;
    Map<String, Map<String, Object>> upstreamOutputs;  // empty in Phase 1, populated Phase 3

    // Backward-compat fields from ExecutionContext:
    Long jobId;
    String javaHome, classpath;
    Map<String, String> envVars;
    AtomicBoolean cancelRequested;
}

@FunctionalInterface
public interface CredentialResolver {
    String resolve(String credentialRef);
}

public static class LogSink {
    public void write(String line);  // delegates to BlockingQueue.offer()
}
```

**Design rationale:**
- Builder pattern (not Lombok @Builder) for explicit control over required vs optional fields
- `CredentialResolver` is a functional interface — the orchestrator wires it with repository + decryption service access; executors never touch those directly
- `LogSink.write(String)` replaces direct `BlockingQueue.offer()` — abstraction allows future log formatting without executor changes
- `upstreamOutputs` pre-plumbed for Phase 3 DAG templating (`${step.<id>.output.X}`)
- Backward-compat fields ensure executors that need `javaHome`, `classpath`, `envVars` don't need the old ExecutionContext

### StepResult + StepStatus

```java
package com.novakai.orchestrator.engine.spi;

public record StepResult(
    StepStatus status,
    Map<String, Object> outputs,
    String message,
    Duration executionTime
) {
    // Factory methods:
    static StepResult success(Map<String,Object> outputs, String msg, Duration time);
    static StepResult failure(String msg, Duration time);

    // Backward-compat:
    boolean isSuccess();           // status == SUCCESS
    int getExitCode();             // outputs.get("exitCode") cast to int, default 0
    String getLogOutput();         // message field
}

public enum StepStatus { SUCCESS, FAILED, SKIPPED }
```

**Design rationale:**
- `outputs` map carries structured data for downstream steps (Phase 3 templating)
- Factory methods avoid verbose constructor calls in executor code
- Backward-compat methods ensure existing result consumers (`setExitCode()`, `setLogOutput()`) continue to work
- `executionTime` measured by orchestrator around the full retry loop

### RetryPolicy

```java
package com.novakai.orchestrator.engine.spi;

public record RetryPolicy(int maxAttempts, Duration delayBetweenAttempts) {
    static RetryPolicy none() { return new RetryPolicy(1, Duration.ZERO); }
    static RetryPolicy fixed(int attempts, Duration delay) { ... }
    int retries() { return Math.max(0, maxAttempts - 1); }
}
```

**Design rationale:**
- `maxAttempts=1` means no retry (one attempt total), not "retry once" — avoids off-by-one confusion
- `retries()` helper returns the number of *additional* attempts beyond the first
- Only fixed-delay retry in Phase 1; exponential backoff can be added later without interface change

---

## Data Model Changes

### JobStep Entity

```java
@Entity
@Table(name = "JOB_STEP")
public class JobStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Changed from @Enumerated(EnumType.STRING) StepType to plain String:
    @Column(name = "STEP_TYPE", nullable = false, length = 50)
    private String stepType;

    @Lob
    @Column(name = "STEP_CONFIG")
    private String stepConfig;  // JSON blob

    // ... other fields unchanged

    // Overloaded setters for backward compat:
    public void setStepType(StepType type) { this.stepType = type.name(); }
    public void setStepType(String type)   { this.stepType = type; }
    public String getStepType()            { return this.stepType; }
    public StepType getStepTypeEnum()      { /* best-effort parse, null for unknown */ }
}
```

**Key decisions:**
- No `AttributeConverter` — the field is already a String, no conversion needed
- `getStepTypeEnum()` returns null (not throws) for unrecognized values — allows graceful handling of plugin-registered types
- `StepType` enum retained in codebase for backward-compat overloads only

### StepExecutorRegistry

```java
@Component
public class StepExecutorRegistry {
    private final ConcurrentHashMap<String, StepExecutor> executors;

    public StepExecutorRegistry(List<StepExecutor> executors) {
        this.executors = new ConcurrentHashMap<>();
        for (StepExecutor e : executors) {
            if (this.executors.putIfAbsent(e.getType(), e) != null) {
                log.warn("Duplicate executor registration for type: {}", e.getType());
            }
        }
    }

    public Optional<StepExecutor> get(String type);
    public List<StepConfigSchema> listAll();
    public Set<String> registeredTypes();
}
```

**Key decisions:**
- `ConcurrentHashMap` for thread-safe O(1) dispatch (no synchronization needed at runtime)
- Constructor injection via Spring's `List<StepExecutor>` — collects all beans implementing the interface
- Duplicate detection logs warning, last-registered wins (Spring bean order is deterministic within a context)

### PluginScanner

```java
@Component
public class PluginScanner implements ApplicationListener<ApplicationReadyEvent> {
    private final StepExecutorRegistry registry;
    @Value("${orchestrator.plugins.dir:}")
    private String pluginsDir;

    // On ApplicationReadyEvent:
    // 1. List JARs in plugins directory
    // 2. For each JAR: create URLClassLoader, load via ServiceLoader<StepExecutor>
    // 3. Register loaded executors into registry (or throw on conflict)
    // @PreDestroy: close all classloaders
}
```

**Key decisions:**
- Runs after `ApplicationReadyEvent` — all Spring beans are fully initialized before plugin loading
- One `URLClassLoader` per JAR — isolation between plugins, no classpath pollution
- Uses Java's standard `ServiceLoader<StepExecutor>` — no custom manifest parsing or service discovery
- Configurable directory via property; empty value = skip scanning

---

## Backward Compatibility Matrix

| Old API | New Equivalent | Compat Method |
|---------|---------------|---------------|
| `ExecutionContext.runId` | `StepContext.runId` | Direct field |
| `ExecutionContext.javaHome` | `StepContext.javaHome` | Backward-compat field |
| `ExecutionContext.classpath` | `StepContext.classpath` | Backward-compat field |
| `ExecutionContext.envVars` | `StepContext.envVars` | Backward-compat field |
| `ExecutionContext.liveLogQueue.offer(line)` | `StepContext.logSink.write(line)` | Abstraction over same queue |
| `ExecutionContext.cancelRequested` | `StepContext.cancelRequested` | Direct field (AtomicBoolean) |
| `(boolean, int, String)` result tuple | `StepResult(status, outputs, message, time)` | `isSuccess()`, `getExitCode()`, `getLogOutput()` |
| `credentialRepo.findByRef(...)` in executor | `context.getCredentials().resolve(ref)` | Functional interface abstraction |
| `StepExecutorFactory.get(StepType)` | `registry.get(step.getStepType())` | String-based lookup |
