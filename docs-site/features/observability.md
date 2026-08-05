# Observability

This page documents the metrics endpoints, Grafana dashboard configuration, and OpenTelemetry tracing integration for Novakai Orchestrator.

## Prometheus Metrics

List of exposed metrics including job execution duration, step success/failure rates, thread pool utilization, and custom business metrics. Endpoint path and authentication requirements.

## Grafana Dashboard

How to import the provided Grafana dashboard JSON, what panels it includes, and recommended alerting rules based on orchestrator metrics.

## OpenTelemetry Tracing

How OTel spans are structured across job execution, including parent-child relationships between run-level and step-level spans, and how to configure a tracing backend (Jaeger, Zipkin).
