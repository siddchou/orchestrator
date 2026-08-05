<!-- FILE: phase5-00-overview.md -->
# Phase 5 — Notifications: Overview

## Goal

Add a pluggable `NotificationChannel` SPI with Email, Slack webhook, and generic webhook implementations. Users subscribe per-job to receive notifications on run completion events. Delivery is async with retry, and all attempts are logged for visibility.

## Scope

### In scope
- `NotificationChannel` SPI interface + `NotificationChannelRegistry` (mirrors Phase 1's `StepExecutorRegistry`)
- Three channel implementations: Email, Slack Webhook, Generic Webhook
- `NOTIFICATION_SUBSCRIPTION` entity/table — per-job subscriptions with event filters
- `NOTIFICATION_DELIVERY_LOG` entity/table — delivery attempt tracking
- Custom Spring `ApplicationEvent` (`JobRunCompletedEvent`) published at run-completion
- `NotificationDispatcher` — listens to the event, resolves subscriptions, dispatches async
- Retry logic: 3 attempts, exponential backoff (1s / 5s / 25s)
- REST CRUD endpoints: `/api/jobs/{jobId}/notifications`
- UI: Notifications tab on job detail page, delivery log viewer

### Out of scope
- SMS, Teams, PagerDuty, or other channels (SPI makes these easy to add later)
- Subscription inheritance from parent jobs
- Rate limiting at the dispatcher level (rely on channel-level retry/backoff)
- Webhook signature verification / HMAC validation

## Assumptions

- **[ASSUMED]** SMTP server will be configured via `application.yml` properties (`spring.mail.*`). No existing mail config means this is environment-specific.
- **[ASSUMED]** Slack webhooks use the Incoming Webhook URL pattern (`https://hooks.slack.com/services/...`), not the Slack Bolt SDK or OAuth app flow.
- **[ASSUMED]** The Oracle database supports `CLOB` for JSON channel configs (consistent with existing `JOB_STEP.config_json`).
- **[ASSUMED]** Users who configure notifications have operational email addresses; no user profile "email on file" concept exists yet — email recipients come from the subscription config.
- **[ASSUMED]** SSE endpoint for run completion does not yet exist in the codebase (per Code Review Findings). The notification dispatcher will hook into status transitions directly, not via SSE.

## Table of Contents

1. [Code Review Findings](phase5-code-review-findings.md) — status transition points, async config, Flyway versions
2. [Overview](phase5-00-overview.md) — this file
3. [SPI Design](phase5-01-notification-spi-design.md) — interface, implementations, dispatcher
4. [Task Breakdown](phase5-02-task-breakdown.md) — 16 PR-sized tasks with DoD
5. [Migration Strategy](phase5-03-migration-strategy.md) — V12 Flyway SQL, rollback
6. [Edge Cases & Failure Modes](phase5-04-edge-cases-and-failure-modes.md) — adversarial scenarios
7. [Testing Plan](phase5-05-testing-plan.md) — unit, integration, forced-failure tests

## Effort Estimate

| Task Group | Stories | T-Shirt | Notes |
|------------|---------|---------|-------|
| SPI + Registry + 3 channels | 2 tasks | M | Core backend, no UI |
| Entities + Repository + Migration | 2 tasks | S | V12 migration, JPA entities |
| Event + Dispatcher + Retry | 2 tasks | M | New event mechanism |
| REST API CRUD | 2 tasks | S | Standard controller pattern |
| UI Notifications Tab | 3 tasks | L | Dynamic form, delivery log table |
| Testing | Ongoing per task | — | Each task includes tests |

**Total: ~16 tasks, 1-2 sprints for a single contributor.**

## Dependencies

- **Phase 1 (StepExecutor SPI):** Registry pattern to mirror. No runtime dependency.
- **Phase 3 (DAG execution):** `DagExecutionEngine.completeRun()` is one of the two hook points. Must be compatible with both linear and DAG execution paths.
- **No blocking dependency on Phase 2 UI components** — but reuses `DynamicStepFormComponent` pattern for channel config forms.
