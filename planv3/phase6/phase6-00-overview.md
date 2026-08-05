<!-- FILE: phase6-00-overview.md -->
# Phase 6 — Observability: Overview

## Scope

Add production-grade observability to the orchestrator engine: Prometheus metrics, structured JSON logging with MDC context propagation, OpenTelemetry tracing spans around run/step execution, and a starter Grafana dashboard.

**No hard dependency on other phases.** This phase can be worked in parallel by a second contributor at any point. It touches only configuration files, the execution engine, and adds new dependencies — no changes to domain models or API contracts are required.

## What's In Scope

| Area | Deliverable |
|---|---|
| **Metrics** | Micrometer Timers/Counters for run duration, step duration (tagged by `stepType` + `status`), run count. Exposed via `/actuator/prometheus`. |
| **Logging** | JSON logging appender (logstash-logback-encoder) alongside existing human-readable console. MDC fields: `runId`, `jobId`, `stepId`, `stepType`. |
| **Tracing** | OpenTelemetry spans around each step execution, tagged with `step.type`, `step.id`, `run.id`. OTLP exporter defaults to no-op if endpoint is unconfigured. |
| **Dashboard** | Starter Grafana dashboard JSON: run throughput, failure rate %, p50/p95 step latency by type, active runs gauge. |

## What's Out of Scope

- Alerting pipelines (PagerDuty, Opsgenie, Slack webhooks) — Phase 7b or later
- Log aggregation infrastructure (ELK, Loki) setup — operational concern
- Distributed tracing across service boundaries — single-service spans only
- Frontend observability (browser RUM, Angular performance)

## Assumptions

| # | Assumption | Risk if Wrong |
|---|---|---|
| A1 | App runs as a single instance. No instance-ID tagging required initially. | Low — metrics design reserves an `instance` tag slot. |
| A2 | Phase 3's concurrent step execution (ThreadPoolTaskExecutor) is already merged. MDC must be thread-safe. | Medium — addressed by using a Spring `TaskDecorator`. |
| A3 | The actuator `/actuator/prometheus` endpoint will sit behind the existing JWT auth. For Prometheus scraping, we'll document IP-based firewall rules or an optional permit-all config behind a path prefix. | Low — documented in edge cases. |
| A4 | Spring Boot 4.1.0 is compatible with `micrometer-registry-prometheus-simpleclient` and `opentelemetry-spring-boot-starter`. | Medium — verify during dependency resolution. |
| A5 | The existing MDC keys `correlationId` and `runId` are already set somewhere in the request pipeline. We'll extend, not replace. | Low — grep confirms usage in logback pattern. |

## Effort Estimate

| Task | Story Points | Confidence |
|---|---|---|
| Metrics (Micrometer + Prometheus) | 3 | High |
| Structured JSON Logging + MDC | 5 | Medium (thread-safety under concurrent execution) |
| OpenTelemetry Tracing | 5 | Medium (OTel auto-instrumentation interaction with manual spans) |
| Grafana Dashboard + Docs | 2 | High |
| Testing | 3 | High |
| **Total** | **18** | — |

## Table of Contents

1. [Code Review Findings](phase6-code-review-findings.md) — actuator config, logging setup, dependency audit
2. [Metrics and Tracing Design](phase6-01-metrics-and-tracing-design.md) — meter catalog, MDC lifecycle, OTel span topology
3. [Task Breakdown](phase6-02-task-breakdown.md) — PR-sized tasks with DoD
4. [Edge Cases and Failure Modes](phase6-03-edge-cases-and-failure-modes.md) — scenario table
5. [Testing Plan](phase6-04-testing-plan.md) — unit + integration + manual verification

## Parallel Execution Note

This phase has **no hard dependency** on Phases 1–5. The only soft dependency is that Phase 1's `StepExecutor.getType()` must exist (it already does per code review). A second contributor can start this phase immediately while others work on different phases.
