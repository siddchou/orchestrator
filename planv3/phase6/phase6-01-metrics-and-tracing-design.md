<!-- FILE: phase6-01-metrics-and-tracing-design.md -->
# Phase 6 — Metrics and Tracing Design

## 1. Micrometer Meters Catalog

### 1.1 `orchestrator.run.duration` (Timer)

Measures wall-clock duration of a complete job run, from first step start to final status write.

| Attribute | Value |
|---|---|
| **ID** | `orchestrator_run_duration_seconds` |
| **Type** | Timer |
| **Tags** | `job_name`, `status` (SUCCESS/FAILED/PARTIAL/CANCELLED) |
| **Recorded at** | `DagExecutionEngine.finalizeRun()` — record `Duration.between(run.startedAt, run.endedAt)` |
| **Cardinality** | Bounded by number of jobs × 4 statuses |

### 1.2 `orchestrator.step.duration` (Timer)

Measures wall-clock duration of each individual step execution, including retries.

| Attribute | Value |
|---|---|
| **ID** | `orchestrator_step_duration_seconds` |
| **Type** | Timer |
| **Tags** | `step_type`, `status` (SUCCESS/FAILED/SKIPPED/CANCELLED) |
| **Recorded at** | Immediately after `StepResult` is returned from `executeStepWithRetry()` — use the existing `result.executionTime()` field |
| **Cardinality** | Bounded by registered step types × 4 statuses. See §5 for cardinality guardrails. |

### 1.3 `orchestrator.run.count` (Counter)

Counts completed job runs by outcome.

| Attribute | Value |
|---|---|
| **ID** | `orchestrator_run_count_total` |
| **Type** | Counter |
| **Tags** | `status` (SUCCESS/FAILED/PARTIAL/CANCELLED) |
| **Incremented at** | `DagExecutionEngine.finalizeRun()` — increment by 1 with the run's final status tag |

### 1.4 `orchestrator.step.count` (Counter)

Counts completed step executions by type and outcome.

| Attribute | Value |
|---|---|
| **ID** | `orchestrator_step_count_total` |
| **Type** | Counter |
| **Tags** | `step_type`, `status` |
| **Incremented at** | Immediately after each step produces a StepResult — increment by 1 |

### 1.5 `orchestrator.run.active` (Gauge)

Tracks the number of currently running job executions.

| Attribute | Value |
|---|---|
| **ID** | `orchestrator_run_active` |
| **Type** | Gauge (via `LongGauge` backed by an `AtomicInteger`) |
| **Tags** | None |
| **Updated at** | +1 when `execute()` begins, -1 in `finalizeRun()` |

## 2. Meter Registration Strategy

Create a dedicated `ObservabilityService` component that pre-registers all meters at startup and provides thread-safe recording methods.

```java
@Component
public class ObservabilityService {
    private final MeterRegistry registry;
    private final AtomicInteger activeRuns = new AtomicInteger(0);

    public ObservabilityService(MeterRegistry registry) {
        this.registry = registry;

        // Gauge — registered once, reads AtomicInteger on scrape
        Gauge.register("orchestrator.run.active", registry, activeRuns, AtomicInteger::get);

        // Counters and Timers are created lazily per tag combination by Micrometer
        // No need to pre-create — just call record/increment with tags at runtime
    }

    public void recordStepDuration(Duration duration, String stepType, StepStatus status) {
        Timer.builder("orchestrator.step.duration")
                .tag("step_type", stepType)
                .tag("status", status.name())
                .register(registry)
                .record(duration);
    }

    public void recordRunDuration(Duration duration, String jobName, RunStatus status) {
        Timer.builder("orchestrator.run.duration")
                .tag("job_name", jobName)
                .tag("status", status.name())
                .register(registry)
                .record(duration);
    }

    public void incrementRunCount(RunStatus status) {
        Counter.builder("orchestrator.run.count")
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void incrementStepCount(String stepType, StepStatus status) {
        Counter.builder("orchestrator.step.count")
                .tag("step_type", stepType)
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void incrementActiveRuns() { activeRuns.incrementAndGet(); }
    public void decrementActiveRuns() { activeRuns.decrementAndGet(); }
}
```

**Thread-safety:** Micrometer meters are thread-safe by design. `timer.record()` and `counter.increment()` are safe to call from concurrent threads. Tags are passed per-call, not stored on the meter object — parallel steps executing simultaneously produce independent tag sets with zero collision risk.

## 3. JSON Logging Appender Configuration

Add a third appender to [logback-spring.xml](src/main/resources/logback-spring.xml):

```xml
<!-- JSON file appender for structured log ingestion -->
<appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}.json</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>${LOG_FILE}.json.%d{yyyy-MM-dd}.%i</fileNamePattern>
        <maxFileSize>50MB</maxFileSize>
        <maxHistory>30</maxHistory>
        <totalSizeCap>2GB</totalSizeCap>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>runId</includeMdcKeyName>
        <includeMdcKeyName>jobId</includeMdcKeyName>
        <includeMdcKeyName>stepId</includeMdcKeyName>
        <includeMdcKeyName>stepType</includeMdcKeyName>
        <includeMdcKeyName>correlationId</includeMdcKeyName>
    </encoder>
</appender>
```

The existing `CONSOLE` and `FILE` appenders remain unchanged for local development readability. The JSON appender is referenced in the `default/web` profile root logger alongside FILE. In the `cli` profile, only CONSOLE is used (no JSON). In `test`, only CONSOLE at WARN level.

## 4. MDC Field Lifecycle

### 4.1 Fields

| MDC Key | Set When | Cleared When | Source |
|---|---|---|---|
| `runId` | Start of `DagExecutionEngine.execute()` | End of `finalizeRun()` (finally block) | `ctx.getRunId()` (UUID string) |
| `jobId` | Start of `execute()` | End of `finalizeRun()` (finally block) | `String.valueOf(ctx.getJobId())` |
| `stepId` | Top of each step's thread-pool callback (`submitStep`) | Finally block inside the same callback | `String.valueOf(step.getStepId())` |
| `stepType` | Same as `stepId` | Same as `stepId` | `step.getStepType()` |

### 4.2 Thread-Safe MDC Under Concurrent Execution

The `DagExecutionEngine` submits steps to a `ThreadPoolTaskExecutor`. Each thread may execute different steps sequentially, so MDC from one step must not leak into the next.

**Solution:** Configure a `TaskDecorator` on the `jobTaskExecutor` bean:

```java
@Bean("jobTaskExecutor")
public ThreadPoolTaskExecutor jobTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    // ... existing pool config from orchestrator.engine.thread-pool-size ...
    executor.setTaskDecorator(new ObservabilityTaskDecorator());
    return executor;
}

class ObservabilityTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        return () -> {
            // Clear any leftover MDC from previous task on this thread
            MDC.clear();
            try {
                runnable.run();
            } finally {
                MDC.clear(); // Guarantee cleanup before next task runs on this thread
            }
        };
    }
}
```

Inside `submitStep`, set step-level MDC at the top of the try block:
```java
MDC.put("stepId", String.valueOf(step.getStepId()));
MDC.put("stepType", step.getStepType());
// (runId and jobId already set by TaskDecorator or parent context)
```

The `TaskDecorator`'s finally block clears everything. This guarantees no cross-step leakage even if an executor throws mid-step.

### 4.3 Run-Level MDC for HTTP-Triggered Runs

For runs triggered via the HTTP API (JobExecutionOrchestrator), set `runId` and `jobId` in MDC before calling `DagExecutionEngine.execute()`. Clear them after completion. For scheduled/CLI-triggered runs, set them inside the orchestrator's entry point.

## 5. Metric Cardinality Guardrails

The `step_type` tag is bounded by registered executors in `StepExecutorRegistry`. However, plugins can register arbitrary types at runtime. To prevent cardinality explosion:

- **Default:** No hard limit — trust the plugin author for now.
- **Configurable cap:** Add property `orchestrator.metrics.max-step-type-cardinality` (default 50). The `ObservabilityService` maintains a `ConcurrentHashMap<String, Boolean>` of seen step types. When a new type arrives and the map exceeds the cap, substitute the tag value with `__other__`.
- **Logging:** When a type is collapsed to `__other__`, log a WARN once (guarded by an atomic boolean per type) so operators know cardinality is being capped.

## 6. OpenTelemetry Span Topology

### 6.1 Auto-Instrumentation

Adding `opentelemetry-spring-boot-starter` automatically instruments:
- Spring MVC controller endpoints (HTTP server spans for every REST call)
- WebClient / RestTemplate calls (HTTP client spans)
- JDBC queries (if using a supported driver — Oracle JDBC may need explicit instrumentation)

### 6.2 Manual Span — Step Execution

Create a span around each step's execution inside `executeStepWithRetry()`:

```java
// In DagExecutionEngine.executeStepWithRetry():
Span stepSpan = tracer.spanBuilder("step.execute")
    .setSpanKind(SpanKind.INTERNAL)
    .setAttribute("step.type", stepType)
    .setAttribute("step.id", String.valueOf(step.getStepId()))
    .setAttribute("step.name", step.getStepName())
    .setAttribute("run.id", ctx.getRunId())
    .startSpan();

try (Scope scope = TracerObservation.createScope(stepSpan)) {
    // ... existing retry loop with executor.execute(stepCtx) ...
    if (result.isSuccess()) {
        stepSpan.setStatus(StatusCode.OK);
    } else {
        stepSpan.setStatus(StatusCode.ERROR, result.message());
    }
} finally {
    stepSpan.end();
}
```

### 6.3 Manual Span — Run Execution

A parent span covering the entire DAG execution lifecycle:

```java
// In DagExecutionEngine.execute():
Span runSpan = tracer.spanBuilder("run.execute")
    .setSpanKind(SpanKind.INTERNAL)
    .setAttribute("run.id", ctx.getRunId())
    .setAttribute("job.name", job.getJobName())
    .setAttribute("job.id", String.valueOf(ctx.getJobId()))
    .startSpan();

try {
    // ... DAG execution logic ...
} finally {
    runSpan.end();
}
```

Step spans become children of the run span via OpenTelemetry's context propagation. Since each step runs on a thread-pool thread, we must explicitly propagate the parent context by starting the step span within a scoped context:

```java
taskExecutor.execute(() -> {
    try (Scope scope = TracerObservation.createScope(runSpan)) {
        // Step spans created here inherit runSpan as parent
    }
});
```

### 6.4 No-Op When Unconfigured

When `otel.exporter.otlp.endpoint` is not set, the OpenTelemetry SDK creates a no-op tracer provider that drops all spans with near-zero overhead (a null check and return). Spring Boot's auto-configuration handles this — **no conditional code needed** in application logic. The manual span calls (`tracer.spanBuilder()`) simply return no-op span objects.

## 7. Actuator Endpoint Exposure Update

Change [application.yml:51](src/main/resources/application.yml:51) from:
```yaml
include: health,info,metrics
```
to:
```yaml
include: health,info,metrics,prometheus
```

The prometheus endpoint renders all Micrometer meters in Prometheus text format at `/actuator/prometheus`.
