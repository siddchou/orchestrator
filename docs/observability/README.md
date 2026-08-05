# Observability Setup Guide

## Prometheus Scraping

The orchestrator exposes metrics at `/actuator/prometheus` in Prometheus text format.

### Prometheus Configuration

Add the following to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'orchestrator'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']  # Replace with your orchestrator host/port
```

If the orchestrator has authentication enabled, add basic auth:

```yaml
    basic_auth:
      username: '<prometheus-user>'
      password: '<prometheus-password>'
```

Or use a bearer token if JWT auth is configured:

```yaml
    bearer_token: '<your-jwt-token>'
```

### Available Metrics

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `orchestrator_run_duration_seconds` | Timer/Histogram | `job_name`, `status` | Wall-clock duration of job runs |
| `orchestrator_step_duration_seconds` | Timer/Histogram | `step_type`, `status` | Wall-clock duration of step executions |
| `orchestrator_run_count_total` | Counter | `status` | Total completed runs by outcome |
| `orchestrator_step_count_total` | Counter | `step_type`, `status` | Total completed steps by type and outcome |
| `orchestrator_run_active` | Gauge | — | Number of currently running jobs |

## Grafana Dashboard

Import the pre-built dashboard:

1. Open Grafana → Configuration → Data Sources → Add Prometheus data source
2. Import → Upload JSON file: `grafana-dashboard.json`
3. Select your Prometheus data source when prompted

The dashboard includes panels for:
- Run throughput by status (runs/min)
- Failure rate percentage
- Step duration p50/p95 broken down by step type
- Active runs gauge

## OpenTelemetry Tracing

To enable tracing, set the OTLP exporter endpoint:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger-collector:4317
java -jar orchestrator.jar
```

Without this property set, the SDK runs in no-op mode (zero overhead).

Manual spans are created for:
- `run.execute` — covers the entire DAG execution lifecycle
- `step.execute` — covers each individual step execution (child of run span)

Span attributes include `run.id`, `job.name`, `job.id`, `step.type`, `step.id`, and `step.name`.

## Structured JSON Logging

When running with the `default` or `web` Spring profile, a JSON log file is written alongside the human-readable log:

```
logs/orchestrator.log      # Human-readable (for local dev)
logs/orchestrator.log.json  # JSON structured (for log ingestion pipelines)
```

Each JSON line includes MDC fields when available: `runId`, `jobId`, `stepId`, `stepType`, `correlationId`.

## Configuration Properties

| Property | Default | Description |
|---|---|---|
| `otel.exporter.otlp.endpoint` | *(empty)* | OTLP collector endpoint for tracing |
| `otel.service.name` | `orchestrator` | Service name reported to OTel collector |
| `orchestrator.metrics.max-step-type-cardinality` | `50` | Max distinct step types before collapsing to `__other__` |
