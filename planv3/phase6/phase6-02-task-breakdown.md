<!-- FILE: phase6-02-task-breakdown.md -->
# Phase 6 — Task Breakdown

## Task 1: Add Prometheus Registry Dependency and Expose Endpoint

| Field | Value |
|---|---|
| **Files Touched** | `pom.xml`, `src/main/resources/application.yml` |
| **Definition of Done** | `micrometer-registry-prometheus-simpleclient` added to pom.xml. Actuator exposure includes `prometheus`. App starts without error and `/actuator/prometheus` returns 200 with JVM/system metrics. |
| **Test to Add** | Integration test: `WebTestClient.get().uri("/actuator/prometheus").exchange().expectStatus().is2xxSuccessful()` |
| **Depends On** | Nothing |

## Task 2: Create ObservabilityService with Meter Recording Methods

| Field | Value |
|---|---|
| **Files Touched** | `src/main/java/.../engine/observability/ObservabilityService.java` (new) |
| **Definition of Done** | Service class provides `recordStepDuration()`, `recordRunDuration()`, `incrementRunCount()`, `incrementStepCount()`, `incrementActiveRuns()`, `decrementActiveRuns()`. All methods are thread-safe. Gauge for active runs registered on construction. |
| **Test to Add** | Unit test: Inject `SimpleMeterRegistry`, call each method, assert meter IDs and tag values via `registry.get(...).timer()` / `.counter()`. |
| **Depends On** | Task 1 |

## Task 3: Wire Metrics Recording into DagExecutionEngine

| Field | Value |
|---|---|
| **Files Touched** | `src/main/java/.../engine/DagExecutionEngine.java` |
| **Definition of Done** | `ObservabilityService` injected via constructor. Step duration/count recorded after each step result in `submitStep()`. Run duration/count recorded in `finalizeRun()`. Active runs gauge incremented at start of `execute()`, decremented in `finalizeRun()`. |
| **Test to Add** | Unit test: Mock `ObservabilityService`, verify `recordStepDuration` called with correct Duration, stepType, and status after a simulated step execution. |
| **Depends On** | Task 2 |

## Task 4: Implement Step-Type Cardinality Guardrail

| Field | Value |
|---|---|
| **Files Touched** | `ObservabilityService.java`, `src/main/resources/application.yml` (new property) |
| **Definition of Done** | `ObservabilityService` tracks seen step types in a ConcurrentHashMap. When count exceeds configurable threshold (default 50), substitutes tag value with `__other__`. Logs a one-time WARN per collapsed type. Property `orchestrator.metrics.max-step-type-cardinality` documented. |
| **Test to Add** | Unit test: Set cap to 3, register 5 distinct step types, verify the 4th and 5th are tagged as `__other__`. Assert WARN logged exactly once per collapsed type. |
| **Depends On** | Task 2 |

## Task 5: Add JSON Logging Appender to logback-spring.xml

| Field | Value |
|---|---|
| **Files Touched** | `pom.xml` (logstash-logback-encoder dependency), `src/main/resources/logback-spring.xml` |
| **Definition of Done** | `net.logstash.logback:logstash-logback-encoder` added to pom. JSON_FILE appender defined with LogstashEncoder, includes MDC keys `runId`, `jobId`, `stepId`, `stepType`, `correlationId`. Enabled in `default/web` profile alongside existing FILE appender. Console and human-readable file appenders unchanged. |
| **Test to Add** | Manual verification: Start app with default profile, tail the `.json` log file, confirm each line is valid JSON containing timestamp, level, logger, message, and MDC fields. |
| **Depends On** | Nothing (parallel with Tasks 1-4) |

## Task 6: Implement ObservabilityTaskDecorator for Thread-Safe MDC

| Field | Value |
|---|---|
| **Files Touched** | `src/main/java/.../engine/observability/ObservabilityTaskDecorator.java` (new), existing `jobTaskExecutor` bean configuration file |
| **Definition of Done** | `ObservabilityTaskDecorator` implements `TaskDecorator`. Clears MDC before and after each runnable. Decorator attached to the `jobTaskExecutor` bean used by `DagExecutionEngine`. |
| **Test to Add** | Unit test: Create decorator, run two runnables sequentially on same thread — first sets MDC key "test=1", second asserts MDC is empty before setting "test=2". Verify no leakage. |
| **Depends On** | Nothing (parallel with Tasks 1-4) |

## Task 7: Set MDC Fields at Run and Step Boundaries

| Field | Value |
|---|---|
| **Files Touched** | `DagExecutionEngine.java`, `JobExecutionOrchestrator.java` |
| **Definition of Done** | In `DagExecutionEngine.execute()`: set `runId` and `jobId` in MDC, clear in finally block. In `submitStep()` callback: set `stepId` and `stepType` in MDC at top of try block (cleared by TaskDecorator). In `JobExecutionOrchestrator.executeStep()`: same pattern for runs triggered via HTTP API. |
| **Test to Add** | Integration test: Trigger a run, read the JSON log file, assert that every log line during step execution contains the correct `runId`, `jobId`, `stepId`, and `stepType` fields. |
| **Depends On** | Task 6 |

## Task 8: Add OpenTelemetry Dependency and Auto-Configuration

| Field | Value |
|---|---|
| **Files Touched** | `pom.xml`, `src/main/resources/application.yml` (OTel properties) |
| **Definition of Done** | `opentelemetry-spring-boot-starter` added to pom. Application starts without error when no OTLP endpoint is configured (no-op mode). When `otel.exporter.otlp.endpoint` is set, spans are exported. HTTP controller endpoints auto-instrumented by Spring Boot starter. |
| **Test to Add** | Integration test: Start app without OTel config, verify no startup errors. Start with a mock OTLP collector (e.g., `otel-testcontainers`), verify HTTP request spans are emitted. |
| **Depends On** | Nothing (parallel with Tasks 1-7) |

## Task 9: Create Manual Spans Around Step Execution

| Field | Value |
|---|---|
| **Files Touched** | `DagExecutionEngine.java` |
| **Definition of Done** | In `executeStepWithRetry()`: create span "step.execute" with attributes `step.type`, `step.id`, `step.name`, `run.id`. Set status OK/ERROR based on StepResult. End span in finally block. Context propagation ensures step spans are children of the run span. |
| **Test to Add** | Unit test: Inject a mock `Tracer`, verify `spanBuilder("step.execute")` called with correct attributes, and that `setStatus()` / `end()` called on the span. |
| **Depends On** | Task 8 |

## Task 10: Create Manual Span Around Run Execution

| Field | Value |
|---|---|
| **Files Touched** | `DagExecutionEngine.java` |
| **Definition of Done** | In `execute()`: create span "run.execute" with attributes `run.id`, `job.name`, `job.id`. End in finally block. Thread-pool tasks inherit this span as parent via context propagation. |
| **Test to Add** | Unit test: Inject mock `Tracer`, verify run span created and ended, step spans are children (verify parent context). |
| **Depends On** | Task 9 |

## Task 11: Create Starter Grafana Dashboard JSON

| Field | Value |
|---|---|
| **Files Touched** | `docs/observability/grafana-dashboard.json` (new), `docs/observability/README.md` (new) |
| **Definition of Done** | Dashboard JSON contains panels for: (1) run throughput (counter rate), (2) failure rate %, (3) p50/p95 step duration histogram by step_type, (4) active runs gauge. README explains how to import the dashboard into Grafana and point Prometheus at `/actuator/prometheus`. |
| **Test to Add** | Manual verification: Import JSON into a local Grafana instance backed by a Prometheus scraping the test app. Confirm panels render without errors. |
| **Depends On** | Task 3 (need to know exact metric names) |

## Task 12: Integration Test — End-to-End Metrics Verification

| Field | Value |
|---|---|
| **Files Touched** | `src/test/java/.../engine/ObservabilityIntegrationTest.java` (new) |
| **Definition of Done** | Spring Boot integration test that triggers a job run with multiple step types, then scrapes `/actuator/prometheus`. Asserts: (a) `orchestrator_step_duration_seconds` contains entries for each step type executed, (b) `orchestrator_run_count_total` incremented by 1, (c) `orchestrator_run_active` returns to 0 after completion. |
| **Test to Add** | The test itself is the verification. |
| **Depends On** | Task 3, Task 5 |
