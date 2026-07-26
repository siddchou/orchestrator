# Plugin Development Guide

This guide explains how to create custom step types for the orchestrator by implementing the `StepExecutor` SPI.

## Overview

A plugin is a Java class that implements `com.novakai.orchestrator.engine.spi.StepExecutor`. There are two ways to load plugins:

| Method | Use Case | Discovery |
|--------|----------|-----------|
| **Classpath** (`@Component`) | Bundled with the app or placed in `lib/` | Spring component scanning — automatic |
| **Plugin directory** (ServiceLoader) | External JARs dropped into a plugins folder at runtime | `META-INF/services` file + `PluginScanner` |

The plugin directory approach is recommended for distributable plugins since it requires no rebuild of the orchestrator.

---

## Quick Start: HELLO_WORLD Executor (Plugin Directory)

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
import java.time.Duration;
import java.util.List;
import java.util.Map;

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

        String name = "World";
        if (ctx.getStepConfig() != null && !ctx.getStepConfig().isBlank()) {
            // parse config from ctx.getStepConfig() JSON string
        }

        ctx.getLogSink().log("Hello, " + name + "!");

        Map<String, Object> outputs = Map.of("greeting", "Hello, " + name + "!");
        return StepResult.success(outputs, "Greeted " + name,
            Duration.ofNanos(System.nanoTime() - startTime));
    }
}
```

Note: No `@Component` annotation needed — the ServiceLoader instantiates via no-arg constructor.

### 3. Register with ServiceLoader

Create the file `src/main/resources/META-INF/services/com.novakai.orchestrator.engine.spi.StepExecutor` with one line:

```
com.example.plugin.HelloWorldStepExecutor
```

### 4. Package as JAR

```bash
mvn package
```

The output is a standard thin JAR (no fat-jar packaging needed — the orchestrator provides all dependencies at runtime).

### 5. Deploy

Copy the JAR into the plugins directory configured by `orchestrator.plugins.dir` (default: `/opt/orchestrator/plugins`), then restart:

```bash
cp target/hello-world-plugin.jar /opt/orchestrator/plugins/
systemctl restart orchestrator
```

The `PluginScanner` component discovers and registers all executor JARs at startup. Check the logs for:

```
INFO  PluginScanner - Scanning 1 JAR(s) in '/opt/orchestrator/plugins' for step executor plugins
INFO  PluginScanner - Registered plugin executor: type='HELLO_WORLD', class=com.example.plugin.HelloWorldStepExecutor
```

---

## Classpath Plugins (Alternative)

For plugins bundled at build time, annotate with `@Component` instead of using ServiceLoader:

```java
import org.springframework.stereotype.Component;

@Component
public class BundledHelloWorldExecutor implements StepExecutor { ... }
```

Spring's component scanning discovers it automatically. No `META-INF/services` file needed. This approach is suitable for internal plugins that ship with the orchestrator and need Spring DI (`@Autowired`).

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.plugins.dir` | `${ORCH_HOME}/plugins` | Directory to scan for plugin JARs at startup |

Set via environment variable or override in `application.yml`:

```yaml
orchestrator:
  plugins:
    dir: /custom/path/to/plugins
```

The scanner runs once at application startup. If the directory doesn't exist, it is silently skipped. Only `.jar` files are considered.

---

## API Reference

### StepExecutor Interface

| Method | Return | Description |
|--------|--------|-------------|
| `getType()` | `String` | Unique type identifier (e.g., `"HTTP_CALL"`). Must be uppercase, no spaces or special characters. |
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
    Map<String, Object> outputs,    // structured data for downstream steps
    String message,                 // human-readable summary
    Duration executionTime          // wall-clock time of execute() call
)
```

Factory methods:
- `StepResult.success(outputs, message, duration)` — status SUCCESS
- `StepResult.failure(message, duration)` — status FAILED with empty outputs

---

## Dependency Injection

Plugins loaded from the plugin directory are **instantiated via ServiceLoader**, not Spring. They cannot use `@Autowired` or constructor injection. All dependencies are accessed through `StepContext`:

| Need | How to Access |
|------|---------------|
| Logging | `ctx.getLogSink().log(line)` |
| Credentials | `ctx.getCredentials().resolve(ref)` |
| Environment vars | `ctx.getEnvVars()` |
| Working directory | `ctx.getWorkDir()` |
| Java home / classpath | `ctx.getJavaHome()`, `ctx.getClasspath()` |

If you need access to Spring beans (JdbcTemplate, RestClient), use the **classpath plugin** approach with `@Component` instead.

---

## Patterns & Conventions

### Timing

Always capture start time at the top of `execute()` and compute duration on every return path:

```java
long startTime = System.nanoTime();
// ... work ...
return StepResult.success(outputs, "done", Duration.ofNanos(System.nanoTime() - startTime));
```

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
String decryptedValue = ctx.getCredentials().resolve(credentialRef);
```

---

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

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Executor not registered (classpath) | Missing `@Component` or JAR not on classpath | Verify annotation and that the JAR is in `lib/` |
| Executor not registered (plugin dir) | Missing `META-INF/services` file or wrong fully-qualified class name | Check the services file exists and lists the correct class |
| No-arg constructor error | ServiceLoader requires a public no-arg constructor | Add one, or use the classpath plugin approach with Spring DI |
| Duplicate type warning | Two executors return the same `getType()` string | Use a unique type identifier per executor |
| Credential resolve fails | Reference doesn't match any stored credential | Check the credential ref matches exactly (case-sensitive) |
| Step times out | Orchestrator's default-step-timeout-minutes exceeded | Increase timeout in application config or optimize executor |
