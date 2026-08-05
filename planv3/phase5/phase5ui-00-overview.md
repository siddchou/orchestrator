# Phase 5 UI — Overview

## Scope

Deliver a polished, production-ready Notifications tab in the job detail page. The backend (NotificationChannel SPI, NotificationController, notification dispatcher) is implemented in a companion plan; this document covers the front-end only.

**In scope:**
1. Fix existing notification UI components (channel label bug, missing empty states, validation gaps)
2. Extract shared `DynamicConfigFormComponent` from `DynamicStepFormComponent` to eliminate form-building duplication
3. Add run-ID filter to delivery log view
4. Improve UX: confirmation dialogs, loading states, error toast messages
5. Ensure all three channel types (Email, Slack Webhook, Generic Webhook) render correct config fields

**Out of scope:**
- Backend notification infrastructure (covered by `phase5-00-overview.md` / `phase5-01-notification-spi-design.md`)
- Real-time push notifications (WebSocket/SSE) — polling-based only
- User-level notification preferences — job-level subscriptions only
- Notification templates or rich content

## Assumptions

1. **Backend is complete:** `/api/notifications` endpoints are implemented and tested per the Phase 5 backend plan
2. **SMTP configured:** Email channel requires `spring.mail.*` properties in `application.yml`; UI gracefully handles missing email channel (backend conditionally registers it)
3. **Slack Incoming Webhook:** User obtains webhook URL from Slack app config; UI doesn't provision webhooks, only stores the URL
4. **FieldDefinition parity:** `ChannelConfigSchema.fields` uses the same `FieldDefinition` record as `StepConfigSchema.fields`; field types overlap (STRING, LIST_STRING, ENUM are common)
5. **Events enum:** Backend returns events as a comma-separated string on read; UI sends an array on create/update and splits/joins accordingly
6. **Oracle CLOB:** Config JSON fits in Oracle CLOB (~4KB for typical notification configs); no special handling needed beyond standard Jackson serialization

## Design Decision: DynamicConfigFormComponent Extraction

**Decision:** Extract a shared `DynamicConfigFormComponent` from the existing `DynamicStepFormComponent`.

**Rationale:**
- Both `StepConfigSchema` and `ChannelConfigSchema` share `FieldDefinition[]` as their field definition format
- The form-building logic (`buildForm`, `fieldValidators`, `resolveInitialValue`, `toConfig`) is identical in intent — only the metadata fields differ (`stepType`/`displayName` vs `type`)
- Current notification subscription form duplicates ~40 lines of this logic manually
- Future phases may add more schema-driven config forms (exporters, connectors, etc.)

**Design:** The extracted component accepts a generic `fields: FieldDefinition[]` input plus an optional `title: string` for section labeling. It provides the same `formReady`/`configValid` events and `validate()`/`toConfig()` methods as DynamicStepFormComponent. The existing DynamicStepFormComponent becomes a thin wrapper around it.

**Alternative considered:** Leave notification form as-is (direct DynamicFieldComponent usage). Rejected — duplication creates divergence risk when field types are added or validation behavior changes.

## Table of Contents

| File | Description |
|------|-------------|
| `phase5ui-code-review-findings.md` | Code review findings from Step 0 |
| `phase5ui-00-overview.md` | This file — scope, assumptions, TOC |
| `phase5ui-01-component-design.md` | Component designs and DynamicConfigForm extraction |
| `phase5ui-02-task-breakdown.md` | Numbered task breakdown with DoD |
| `phase5ui-03-edge-cases-and-failure-modes.md` | Edge cases, failure modes, error handling |
| `phase5ui-04-testing-plan.md` | Unit tests and E2E test scenarios |

## Effort Estimate

~10 tasks across 2-3 PRs. See `phase5ui-02-task-breakdown.md` for the full breakdown.

**T-shirt:** Medium (1.5 - 2 engineer-days)

**Dependencies:**
- Phase 5 backend must be deployed to exercise endpoints
- Phase 2 DynamicStepFormComponent must be stable (it is — it's on this branch)

## File Organization

```
orchestrator-ui/src/
├── app/
│   ├── core/
│   │   ├── models/
│   │   │   └── notification.model.ts       (exists)
│   │   └── services/
│   │       └── notification.service.ts     (exists)
│   ├── features/jobs/notifications/
│   │   ├── notifications-tab.component.*   (exists — fix bugs, add empty state)
│   │   ├── notification-subscription-form.component.*  (exists — refactor to use DynamicConfigForm)
│   │   └── delivery-log.component.*        (exists — add run-ID filter)
│   └── shared/components/
│       ├── dynamic-config-form/            (NEW — extracted from DynamicStepForm)
│       │   ├── dynamic-config-form.ts
│       │   ├── dynamic-config-form.html
│       │   └── dynamic-config-form.scss
│       ├── dynamic-field/                  (exists — no changes needed)
│       └── dynamic-step-form/              (exists — becomes wrapper around DynamicConfigForm)
└── ...
```
