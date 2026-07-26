# Plugin Development Guide

This guide explains how to create custom step types for the orchestrator by implementing the `StepExecutor` SPI.

## Overview

A plugin is a Java class that implements `com.novakai.orchestrator.engine.spi.StepExecutor`. Spring Boot's component scanning discovers it at startup — no factory registration, no XML config, no service loader files.

## Quick Start: HELLO_WORLD Executor

### 1. Create a Maven module

```xml
<dependencies>
    <!-- Only the SPI interfaces are needed at compile time -->
    <dependency>
        <groupId>com.novakai</groupId>
        <artifactId>orchestrator</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 2. Implement StepExecutor

```java
package com.example.plugin;

import com.novakai.orchestrator.engine.spi.*;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class HelloWorldStepExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "HELLO_WORLD";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema(
            "HELLO_WORLD",
            "Hello World",
            List.of(
                new FieldDefinition("name", "Name", FieldType.STRING, true, null, null,
                    "Name to greet")
            )
        );
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long startTime = System.nanoTime();

        String name = "World"; // default
        if (ctx.getStepConfig() != null && !ctx.getStepConfig().isBlank()) {
            // parse config from ctx.getStepConfig() JSON string
            // use JsonParser or your preferred library
        }

        ctx.getLogSink().log("Hello, " + name + "!");

        Map<String, Object> outputs = Map.of("greeting", "Hello, " + name + "!");
        return StepResult.success(outputs, "Greeted " + name,
            Duration.ofNanos(System.nanoTime() - startTime));
    }
}
```

### 3. Package as JAR

Build with your build tool (Maven/Gradle). The output is a standard JAR containing the compiled class and a `@Component` annotation.

### 4. Deploy

Copy the JAR into the orchestrator's `lib/` directory alongside `orchestrator.jar`, then restart:

```bash
cp target/hello-world-plugin.jar /opt/orchestrator/lib/
systemctl restart orchestrator
```

Spring Boot picks up all JARs in `lib/`. The `@Component` annotation triggers automatic discovery. No additional configuration needed.

## API Reference

### StepExecutor Interface

| Method | Return | Description |
|--------|--------|-------------|
| `getType()` | `String` | Unique type identifier (e.g., `"HTTP_CALL"`). Used for registry dispatch and DB storage. Must be uppercase, no spaces or special characters. |
| `getConfigSchema()` | `StepConfigSchema` | Declares the step's configuration fields. Drives UI form generation and runtime validation. |
| `execute(StepContext ctx)` | `StepResult` | Performs the step work. Called by the orchestrator for each job run. |
| `defaultRetryPolicy()` | `RetryPolicy` | Optional override. Defaults to `RetryPolicy.none()`. |

### StepConfigSchema

```java
record StepConfigSchema(
    String stepType,           // must match getType()
    String displayName,        // human-readable label for UI
    List<FieldDefinition> fields  // ordered list of config fields
)
```

### FieldDefinition

```java
record FieldDefinition(
    String name,              // JSON key in step_config
    String label,             // display label in UI
    FieldType type,           // STRING | NUMBER | BOOLEAN | ENUM | SECRET_REF | FILE_PATTERN
    boolean required,         // orchestrator validates presence before calling execute()
    Object defaultValue,      // initial value for UI forms
    List<String> enumValues,  // required when type == ENUM
    String helpText           // tooltip in UI
)
```

### StepContext (what you receive)

| Method | Returns | Description |
|--------|---------|-------------|
| `getStepConfig()` | `String` | Raw JSON config from the job step definition |
| `getCredentials().resolve(ref)` | `String` | Resolves a credential reference to its decrypted value. Throws if not found. |
| `getLogSink().log(line)` | `void` | Writes to the live log stream (SSE) and persistent storage |
| `getWorkDir()` | `Path` | Job's working directory |
| `getJavaHome()` / `setJavaHome(v)` | `String` | Set by ENV_SETUP step, readable by downstream steps |
| `getClasspath()` / `setClasspath(v)` | `List<String>` | Set by ENV_SETUP step |
| `getEnvVars()` | `Map<String,String>` | Environment variables merged from job config and ENV_SETUP |
| `isCancelRequested()` | `boolean` | Check in long-running loops to support cancellation |

### StepResult (what you return)

```java
record StepResult(
    StepStatus status,              // SUCCESS | FAILED | SKIPPED
    Map<String, Object> outputs,    // structured data for downstream steps (Phase 3)
    String message,                 // human-readable summary
    Duration executionTime          // wall-clock time of execute() call
)
```

Factory methods:
- `StepResult.success(outputs, message, duration)` — status SUCCESS
- `StepResult.failure(message, duration)` — status FAILED with empty outputs

## Patterns & Conventions

### Timing

Always capture start time at the top of `execute()` and compute duration on every return path:

```java
long startTime = System.nanoTime();
// ... work ...
return StepResult.success(outputs, "done", Duration.ofNanos(System.nanoTime() - startTime));
```

The orchestrator uses this for per-step timing metrics.

### Logging

Write to `ctx.getLogSink().log(line)` instead of `System.out` or SLF4J. This ensures logs appear in the live SSE stream and are persisted with the run.

### Cancellation

Check `ctx.isCancelRequested()` in long-running loops:

```java
for (File file : files) {
    if (ctx.isCancelRequested()) break;
    process(file);
}
```

### Error Handling

Return a failure result rather than throwing exceptions when possible. The orchestrator catches all exceptions, but returning `StepResult.failure()` gives you control over the error message and outputs map.

### Credential Resolution

For sensitive values (passwords, private keys), declare the field as `FieldType.SECRET_REF` in your schema. Users provide a credential reference string, and you resolve it at runtime:

```java
String credentialRef = (String) config.get("credentialRef");
String decryptedValue = ctx.getCredentials().resolve(credentialRef);
```

The resolver throws if the reference is not found — let it propagate; the orchestrator catches it.

## Testing

Write unit tests that construct `StepContext` with a builder:

```java
var ctx = StepContext.builder()
    .stepConfig("{\"name\":\"Test\"}")
    .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
    .workDir(Paths.get("/tmp"))
    .envVars(Map.of("PATH", "/usr/bin"))
    .build();

StepResult result = executor.execute(ctx);
assert result.isSuccess();
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Executor not registered | Missing `@Component` or JAR not on classpath | Verify annotation and that the JAR is in `lib/` |
| Duplicate type warning | Two executors return the same `getType()` string | Use a unique type identifier per executor |
| Credential resolve fails | Reference doesn't match any stored credential | Check the credential ref matches exactly (case-sensitive) |
| Step times out | Orchestrator's default-step-timeout-minutes exceeded | Increase timeout in application config or optimize executor |
