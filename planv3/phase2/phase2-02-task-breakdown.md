# Phase 2 — Task Breakdown (Remaining Work)

All core Phase 2 components are implemented. Tasks below address gaps, polish, and verification.

---

## T1: Replace hardcoded step palette metadata with API-driven data

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.ts`, `step-palette.html` |
| **DoD** | Step types render using `displayName` from schema response. Unknown step types get a default icon (`settings`) and no description (or displayName as description). Hardcoded metadata map removed or used only as an optional override for icons. |
| **Test** | Unit test: mock schema with unknown step type → verifies default icon + displayName rendering |
| **Depends On** | Nothing |

---

## T2: Harden edge cases in dynamic forms

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/shared/components/dynamic-field/dynamic-field.ts`, `dynamic-step-form.ts` |
| **DoD** | Unsupported FieldType renders a clear warning message (not just a silent fallback input). Schema with empty fields list renders gracefully. Null/undefined schema handled without crash. SECRET_REF field shows validation error when credential is deleted mid-edit. |
| **Test** | Unit tests for: unknown FieldType, empty schema, null schema, missing credential reference |
| **Depends On** | Nothing |

---

## T3: Verify server-side team scoping on backend APIs

| Field | Value |
|-------|-------|
| **Files Touched** | Java controller/service layer (scope to be determined by investigation) |
| **DoD** | All job-related API endpoints (`/api/jobs/*`, `/api/runs/*`, `/api/credentials/*`) filter results by team ID from JWT claim or request attribute. Cross-team access returns 403, not 200 with empty results. Admin users can optionally see all teams (role-based override). |
| **Test** | Integration tests: create jobs in two teams, verify API responses are scoped correctly |
| **Depends On** | Nothing — but requires investigation of current backend state first |

---

## T4: Fill test coverage gaps — high-value components

| Field | Value |
|-------|-------|
| **Files Touched** | `job-detail.component.spec.ts` (new), `step-form-dialog.spec.ts` (new), `theme.service.spec.ts` (new), `form-guard.service.spec.ts` (new) |
| **DoD** | Each component has ≥80% branch coverage. Key paths tested: drag-and-drop reordering in JobDetail, schema fetch + form build in StepFormDialog, signal toggling + localStorage persistence in ThemeService, dirty/clean state transitions in FormGuard. |
| **Test** | Vitest unit tests, run with `npx vitest run` |
| **Depends On** | Nothing |

---

## T5: E2E smoke test suite

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/e2e/` (new directory), vitest or Playwright config |
| **DoD** | Critical path covered: login → view job list → create job → add step via palette → configure step form → save job → trigger run → view run detail with timeline → switch team → verify scoped data. Runs in CI. |
| **Test** | E2E test runner (Playwright preferred, or Cypress) |
| **Depends On** | T1, T2 (polish should land before E2E baseline) |

---

## T6: Dark mode audit and polish

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/styles.scss` |
| **DoD** | All interactive elements inspected in dark mode: buttons, inputs, selects, chips, dialogs, tooltips, snack bars, form validation messages. No inverted or low-contrast text. Dialog backdrop and overlay render correctly. |
| **Test** | Manual visual audit with screenshots (no automated test needed) |
| **Depends On** | Nothing |

---

## T7: Team switcher — improve UX during reload

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/shared/components/team-switcher/team-switcher.ts`, `app.ts` |
| **DoD** | Instead of full page reload, team switch could use a soft navigation approach: invalidate cached data, re-fetch from API, preserve router state. If full reload is required for correctness, show a loading indicator during the transition rather than an abrupt reload. |
| **Test** | Manual verification + unit test for form guard integration |
| **Depends On** | Nothing |

---

## T8: StepFormDialog — handle step type removal gracefully

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/features/jobs/step-builder/step-form-dialog.ts` |
| **DoD** | When editing a step whose type has been removed from the registry, the dialog displays a clear warning: "Step type 'X' is no longer available. You can view existing config but cannot save changes." Edit button disabled. Schema fallback shows fields as read-only key-value pairs. |
| **Test** | Unit test: mock 404 from schema fetch → verifies warning state |
| **Depends On** | Nothing |

---

## Task Dependency Graph

```
T1 (palette API-driven)    T2 (form hardening)    T6 (dark mode audit)
        │                        │                       │
        ▼                        ▼                       ▼
     T5 ──────────────── T3 (team scoping)   T7 (team switch UX)
        │
        ▼
     (no blockers)

T4 (test coverage gaps) — independent of all other tasks
T8 (step type removal)  — independent, can run in parallel
```

## Execution Order Recommendation

1. **Week 1:** T2 + T6 + T8 (quick wins, no dependencies, polish items)
2. **Week 1-2:** T3 (backend investigation + scoping verification — may uncover more work)
3. **Week 2:** T1 + T4 + T7 (parallelizable)
4. **Week 3:** T5 (E2E suite — depends on polish landing first)
