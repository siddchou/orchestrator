<!-- FILE: phase6-04-testing-plan.md -->
# Phase 6 — Testing Plan

## Unit Tests

### UT-1: ObservabilityService Meter Registration and Tag Correctness

| What | Verify each recording method creates meters with correct IDs and tag values |
|---|---|
| Setup | Inject `SimpleMeterRegistry` into `ObservabilityService` |
| Assertions | `registry.get("orchestrator.step.duration").timer().getId()` has tags `{step_type=shell_exec, status=SUCCESS}` after calling `recordStepDuration(1s, "shell_exec", SUCCESS)` |
| Coverage | All 5 methods: `recordStepDuration`, `recordRunDuration`, `incrementRunCount`, `incrementStepCount`, active runs gauge |

### UT-2: Step-Type Cardinality Cap

| What | Verify step types beyond the cap are tagged as `__other__` |
|---|---|
| Setup | Configure cap to 3. Call `recordStepDuration` with 5 distinct step types |
| Assertions | Types 1-3 have their actual name in tags. Types 4-5 are tagged `step_type=__other__`. WARN logged exactly twice (once per collapsed type). |

### UT-3: ObservabilityTaskDecorator MDC Isolation

| What | Verify MDC is cleared between tasks on the same thread |
|---|---|
| Setup | Create decorator. Run two runnables sequentially — first sets `MDC.put("key","val1")`, second asserts `MDC.get("key")` is null before setting `"val2"` |
| Assertions | No leakage from runnable 1 to runnable 2. MDC empty after both complete. |

### UT-4: Active Runs Gauge Increment/Decrement

| What | Verify gauge reflects concurrent run count |
|---|---|
| Setup | Call `incrementActiveRuns()` ×3, assert gauge value is 3. Call `decrementActiveRuns()` ×2, assert value is 1. |
| Assertions | AtomicInteger backing the gauge matches expected value after each operation. Thread-safe under concurrent increment/decrement (use `CountDownLatch` to fan out threads). |

### UT-5: ObservabilityService Fault Tolerance

| What | Verify metric recording failures don't propagate exceptions |
|---|---|
| Setup | Inject a MeterRegistry that throws on `timer.record()` |
| Assertions | `recordStepDuration()` returns without throwing. Error logged at DEBUG level. |

## Integration Tests

### IT-1: Prometheus Endpoint Exposure

| What | `/actuator/prometheus` returns 200 with expected metric families |
|---|---|
| Setup | Spring Boot test with `@AutoConfigureMockMvc`. No OTel config (no-op mode). |
| Assertions | GET `/actuator/prometheus` → 200. Response body contains `orchestrator_step_duration_seconds`, `orchestrator_run_count_total`, `orchestrator_run_active`. Contains standard JVM metrics (`jvm_memory_used_bytes`). |

### IT-2: Step-Level Latency by Type After Job Run

| What | Trigger a job run with multiple step types, verify per-type latency metrics |
|---|---|
| Setup | Integration test with in-memory H2 database. Create a job definition with 3 steps of different types (shell_exec, db_query, java_exec). Trigger via `DagExecutionEngine.execute()`. |
| Assertions | After completion: `/actuator/prometheus` contains `orchestrator_step_duration_seconds_count{step_type="shell_exec"}` = 1, same for `db_query` and `java_exec`. Sum of all step counts = 3. Run count incremented by 1. Active runs gauge = 0. |

### IT-3: Concurrent Step Execution Metrics Safety

| What | Verify metrics don't collide when steps run in parallel |
|---|---|
| Setup | Create a DAG with 5 independent root steps (no dependencies), each of a different type. Trigger execution. All 5 should run concurrently (semaphore allows it). |
| Assertions | After completion: Each step type has exactly 1 count in `orchestrator_step_count_total`. No missing or double-counted entries. Step durations are non-zero and distinct. |

### IT-4: JSON Logging with MDC Context

| What | Verify JSON log output contains correct MDC fields per step |
|---|---|
| Setup | Integration test with `@TestPropertySource` pointing to a temp directory for logs. Trigger a run with 2 steps. Read the `.json` log file. |
| Assertions | Lines during step 1 execution contain `"stepId":"<step1_id>"` and `"stepType":"<type1>"`. Lines during step 2 contain step 2's values. No line contains mixed MDC from both steps. |

### IT-5: OTel No-Op Mode Startup

| What | App starts cleanly without OTel endpoint configured |
|---|---|
| Setup | Spring Boot test with no `otel.*` properties set. |
| Assertions | Application context loads without errors. No WARN/ERROR logs containing "OTLP" or "exporter". Manual span calls in `DagExecutionEngine` don't throw. |

## Manual Verification Steps

### MV-1: Grafana Dashboard Import

1. Start the orchestrator app locally with a test job definition
2. Start Prometheus with a scrape config pointing at `http://localhost:8080/actuator/prometheus`
3. Start Grafana (Docker: `docker run -p 3000:3000 grafana/grafana`)
4. Add Prometheus as a data source in Grafana
5. Import `docs/observability/grafana-dashboard.json`
6. Trigger several job runs manually via the UI or API
7. Verify each panel renders:
   - **Run Throughput** — shows count of completed runs over time
   - **Failure Rate %** — shows percentage of FAILED/PARTIAL runs
   - **Step Latency Histogram** — shows p50/p95 broken down by step type
   - **Active Runs Gauge** — shows 0 when idle, >0 during execution

### MV-2: Structured Log Inspection

1. Start the app with default profile
2. Trigger a job run
3. Tail `logs/orchestrator.log.json`
4. Pipe through `jq .` to verify each line is valid JSON
5. Verify fields present: `@timestamp`, `level`, `logger_name`, `message`, `runId`, `jobId`, `stepId`, `stepType`, `thread_name`

### MV-3: OTel Span Verification (Optional)

1. Start a local Jaeger all-in-one container: `docker run -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one`
2. Set `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` on the orchestrator app
3. Trigger a job run with multiple steps
4. Open Jaeger UI at `http://localhost:16686`
5. Verify trace shows "run.execute" as root span with "step.execute" child spans for each step
6. Verify step attributes contain correct `step.type`, `step.id`, `run.id`
