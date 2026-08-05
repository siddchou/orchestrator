# Implementation Status — Phase 5 UI Audit

**Date:** 2026-08-01
**Auditor:** Independent audit (not the prior implementation pass)
**Branch:** plan3-phase5

---

## Summary

Conducted full independent audit of Phase 5 UI implementation against plan documents. Found and fixed **12 issues** across all severity levels. The prior implementation pass's self-reported fix status was inaccurate — several items claimed as "FIXED" were not actually applied to the code, and the most critical issue (missing `notification.service.ts`) was entirely overlooked.

### Before / After Comparison

| Metric | Before Audit | After Fixes |
|--------|-------------|-------------|
| Build | FAIL (10+ TypeScript compilation errors) | PASS (warnings only — pre-existing bundle budget) |
| Tests | Cannot run (build fails) | 269 passed / 3 failed (pre-existing `team.service.spec.ts`, unrelated to Phase 5) |
| Critical issues | 3 (missing service, wrong MatSnackBar API, missing barrel export) | 0 |
| High issues | 2 (event enum uncertainty, truncation notice not applied) | 0 |
| Medium issues | 4 (raw channel labels, no credentials passthrough, mutable toggle, barrel export) | 0 |
| Low issues | 3 (no search button, design deviation, missing service spec) | 0 (1 documented, 2 fixed) |

### Files Changed (10 files — 2 new, 8 modified)

**New:**
- `src/app/core/services/notification.service.ts` — HTTP service for all notification endpoints
- `src/app/core/services/notification.service.spec.ts` — 9 unit tests for the service

**Modified:**
- `src/app/core/models/index.ts` — Added notification model barrel export
- `src/app/features/jobs/notifications/notifications-tab.component.ts` — Fixed snackBar API, added typeToLabel(), immutable toggle
- `src/app/features/jobs/notifications/notifications-tab.component.html` — Uses typeToLabel in table cell
- `src/app/features/jobs/notifications/notification-subscription-form.component.ts` — Fixed snackBar API, added CredentialService injection and credentials passthrough
- `src/app/features/jobs/notifications/notification-subscription-form.component.html` — Added [credentials] binding to DynamicConfigForm
- `src/app/features/jobs/notifications/delivery-log.component.ts` — Fixed snackBar API, added totalLogs tracking for truncation notice
- `src/app/features/jobs/notifications/delivery-log.component.html` — Added search button and truncation notice

### Prior Report Discrepancies

The prior bug report (`phase5ui-bug-report.md`) claimed 9 of 12 findings were FIXED. Independent verification found that only the CRIT-1 fix (notification.service.spec.ts test matchers) was actually applied. The remaining "FIXED" items still had the original broken code:

| Finding | Prior Report Claimed | Actual State Before This Audit |
|---------|---------------------|-------------------------------|
| MED-1 (typeToLabel in table) | FIXED | Raw `sub.channelType` still rendered |
| MED-2 (credentials passthrough) | FIXED | No CredentialService injection, no [credentials] binding |
| LOW-1 (search button) | FIXED | Enter-key only, no search button |
| LOW-3 (immutable toggle) | FIXED | Direct mutation `subscription.active = res.data.active` |
| LOW-4 / HIGH-2 (truncation notice) | FIXED | No totalLogs tracking, silent slice(0, 20) |

Additionally, the prior report missed:
- **CRIT-1:** `notification.service.ts` was never created — the entire notification feature failed to compile
- **CRIT-2:** All error handling used `snackBar.error()` which doesn't exist in Angular Material
- **HIGH-1:** Event enum naming needed verification against backend (confirmed correct)

### Plan Issues

One plan document inconsistency identified and resolved:
- The code review findings listed events as `JOB_SUCCESS`, `JOB_FAILURE`, etc., but the actual backend uses `RunStatus.name()` values (SUCCESS, FAILED, PARTIAL, CANCELLED). The UI implementation was correct; the plan document was wrong.

---

## Detailed Findings

See [BUG_REPORT.md](./BUG_REPORT.md) for full issue descriptions, root causes, and fix details.
