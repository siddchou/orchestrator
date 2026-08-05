<!-- FILE: phase6-code-review-findings.md -->
# Phase 6 — Code Review Findings

## 1. Actuator Exposure Configuration

- **Dependency present:** `spring-boot-starter-actuator` at [pom.xml:52](src/main/java/pom.xml:52)
- **Current exposure:** `management.endpoints.web.exposure.include=health,info,metrics` ([application.yml:51](src/main/resources/application.yml:51))
- **Prometheus NOT exposed:** The `prometheus` endpoint is absent from the exposure list. There is a config line `management.metrics.export.prometheus.enabled: true` at [application.yml:62](src/main/resources/application.yml:62), but without the `micrometer-registry-prometheus` dependency, this property has no effect.
- **Health probes enabled:** `management.endpoint.health.probes.enabled: true` ([application.yml:56](src/main/resources/application.yml:56))

## 2. Current Logging Setup

- **Config file:** [logback-spring.xml](src/main/resources/logback-spring.xml)
- **Appenders:** Two appenders — `CONSOLE` and `FILE` (RollingFileAppender, 50MB max, 30 days retention, 1GB total cap).
- **Format:** Human-readable pattern only. No JSON/structured logging exists.
- **Pattern includes MDC-like placeholders:** `%X{correlationId:+[corr=%X{correlationId}]}%X{runId:+[run=%X{runId}]}` — these are conditional Logback MDC lookups, meaning the code already attempts to set `correlationId` and `runId` in MDC somewhere.
- **Profiles:** Three profiles (`default/web`, `cli`, `test`) with different log levels.

## 3. Micrometer / Prometheus / OpenTelemetry Dependencies

| Dependency | Present? | Notes |
|---|---|---|
| `spring-boot-starter-actuator` | ✅ Yes (line 52) | Brings Micrometer core automatically |
| `micrometer-registry-prometheus` | ❌ Absent | Must be added |
| `opentelemetry-spring-boot-starter` | ❌ Absent | Must be added |
| `logstash-logback-encoder` | ❌ Absent | Must be added for JSON logging |

**Confirmed:** A grep for `micrometer\|prometheus\|opentelemetry\|otel` in pom.xml only returned the actuator starter line. No Prometheus registry, no OTel, no logstash encoder.

## 4. Existing Timing Data on StepResult

- **StepResult already carries timing:** [StepResult.java:14](src/main/java/com/novakai/orchestrator/engine/spi/StepResult.java:14) has `Duration executionTime` as a record component.
- **DagExecutionEngine measures wall-clock time:** [DagExecutionEngine.java:600-638](src/main/java/com/novakai/orchestrator/engine/DagExecutionEngine.java:600) wraps each step's retry loop with `System.nanoTime()` and passes the duration to StepResult.
- **Perfect for metrics tagging:** The `executionTime` field can be directly recorded into a Micrometer Timer without re-measuring.

## 5. Multi-Instance / Clustering Consideration

- **[NOT FOUND] No clustering infrastructure.** Grep for `instance|cluster|eureka|consul|zookeeper|kubernetes` in application.yml returned no matches beyond the standard `spring.application.name: orchestrator`.
- **Assumption:** The app currently runs as a single instance. No instance-ID tagging is needed initially, but metrics design should预留 (reserve) an `instance` tag for future multi-instance deployments.

## 6. Concurrent Execution Model (Phase 3 Impact)

- **DagExecutionEngine uses ThreadPoolTaskExecutor:** [DagExecutionEngine.java:262](src/main/java/com/novakai/orchestrator/engine/DagExecutionEngine.java:262) submits each step to a thread pool via `taskExecutor.execute()`.
- **Semaphore-based concurrency control:** A `Semaphore(maxConcurrency)` limits parallel steps. Default max is 5 ([DagExecutionEngine.java:101](src/main/java/com/novakai/orchestrator/engine/DagExecutionEngine.java:101)).
- **MDC context MUST NOT leak across threads.** Each thread-pool callback must set and clear MDC fields independently. `MDC.put()` / `MDC.clear()` are not thread-safe across executors — need a wrapper or `TaskDecorator`.

## 7. Security Configuration for Actuator Endpoints

- **Security config exists:** [SecurityConfig.java](src/main/java/com/novakai/orchestrator/api/config/SecurityConfig.java) defines the filter chain.
- **[NOT READ] Exact actuator authorization rules unknown.** The plan must verify whether `/actuator/prometheus` will be protected by default Spring Security or needs explicit permit-all with IP restriction guidance.

## Summary of Gaps to Address in Phase 6

1. Add `micrometer-registry-prometheus` dependency and expose the prometheus endpoint
2. Replace/add JSON logging appender alongside existing human-readable console
3. Wire MDC fields (`runId`, `jobId`, `stepId`, `stepType`) at step execution boundaries with thread-safe clearing
4. Add OpenTelemetry starter and create manual spans around step execution
5. Design metrics to be safe under concurrent step execution (no shared mutable state in tags)
6. Produce a starter Grafana dashboard JSON
