# Phase 2 — Edge Cases and Failure Modes

## Edge Case Matrix

| # | Scenario | Current Behavior | Required Handling | Status | Severity |
|---|----------|-----------------|-------------------|--------|----------|
| E1 | User adds step with a FieldType not in `KNOWN_TYPES` | Fallback `<input matInput>` renders silently — user sees a plain text box for an unknown type | Show warning banner: "Field 'X' uses an unsupported type 'Y'. Contact admin." Disable the field. | **Partial** — fallback exists but no explicit warning message | Medium |
| E2 | User switches team while editing a job with unsaved changes | FormGuardService checks dirty state; if dirty, confirms before switching | Working as designed. Ensure confirmation dialog is clear about data loss risk. | **Handled** | Low |
| E3 | Job editor loads a step whose schema returns 404 from API | StepFormDialog has `isStepTypeRemoved` getter — but behavior depends on implementation detail | Show warning: "Step type 'X' is no longer registered." Display config as read-only key-value pairs. Disable save. | **Partial** — detection exists, UX unclear | Medium |
| E4 | Two users edit the same job simultaneously | Last write wins — no conflict detection | Acceptable for MVP. Consider optimistic locking (version column on JOB_DEFINITION — see V10 migration) in Phase 3. | **Known limitation** | Low |
| E5 | Step executor removed from registry while jobs reference it | `isStepTypeRemoved` getter in StepFormDialog handles the edit case. Run execution is a separate concern. | UI: warn user that step type is unavailable. Backend: decide whether to block runs of jobs with removed step types. | **Partial** — UI side handled, runtime behavior TBD | Medium |
| E6 | SECRET_REF field references a credential that was deleted | DynamicStepFormComponent has credential validation — validates against current credentials list | On load: detect orphaned reference → show error on the field. Prevent save until resolved. | **Handled** — validation exists in component | Low |
| E7 | Schema returns with zero fields | DynamicStepFormComponent renders an empty form — no fields to display | Acceptable for step types that require no configuration (e.g., a "wait" or "notify" step). Ensure submit works with empty config. | **Handled** | Low |
| E8 | LIST_STRING field receives non-array value from backend | `toConfig()` handles array conversion. Initial load: check if existingConfig value is string vs. array. | Parse comma-separated string as fallback if array expected but string received (legacy data compatibility). | **Unknown** — depends on data consistency | Low |
| E9 | ENUM field's `enumValues` is null or empty | FieldDefinition constructor validates ENUM ↔ enumValues consistency on the backend. Frontend: `*ngFor` over null would crash. | Add null guard in template: `*ngIf="fieldDef.enumValues && fieldDef.enumValues.length"`. Show "No options available" if empty. | **Unknown** — backend validates, but frontend defensive check needed | Medium |
| E10 | Network failure during step type list fetch | StepPaletteComponent shows loading state indefinitely or crashes on null response | Add error handler: show "Failed to load step types. Retry?" button with exponential backoff. | **Unknown** — depends on error interceptor behavior | Medium |
| E11 | Run timeline receives a run with zero steps | No bars to render. Time axis has no range. | Show message: "No steps recorded for this run." Graceful empty state. | **Unknown** | Low |
| E12 | Team API fails on initial load | TeamSwitcherComponent has retry logic + sessionStorage cache fallback | Working as designed. Ensure fallback data is stale-time-aware (don't show teams cached from >24h ago without warning). | **Handled** | Low |
| E13 | User belongs to no teams | TeamService returns empty array. TeamSwitcher has nothing to display. | Show alert: "You are not assigned to any team. Contact admin." Block job creation until assigned. | **Unknown** — likely unhandled | High |
| E14 | Dark mode CSS misses a component | Element renders with light-mode colors on dark background (or vice versa) | Visual audit required. See T6 in task breakdown. Common offenders: dialog backdrop, tooltip, snackbar, autocomplete panel. | **Known risk** — audit needed | Medium |
| E15 | Step config JSON exceeds payload size limit | Backend rejects with 413. Frontend shows generic error. | Add client-side character count on config JSON. Show "Config too large (X/Y chars)" warning near submit button. | **Unhandled** | Low |
| E16 | Drag-and-drop reordering conflicts with step dependencies | V8 migration adds dependency columns. Drag reorder changes step order but doesn't update dependencies. | Visual indicator when drag target would violate dependency graph. Or disable drag for dependent steps until Phase 2b DAG canvas lands. | **Unhandled** — interacts with V8 schema | Medium |
| E17 | Theme toggle during form validation | `[data-theme]` attribute changes mid-validation. Error messages may flicker between themes. | Acceptable cosmetic issue. No data integrity risk. | **Cosmetic only** | Low |

---

## Failure Mode Categories

### Data Integrity Failures

| Scenario | Impact | Mitigation |
|----------|--------|-----------|
| TEAM_ID backfill misses rows | Jobs unassigned to any team → invisible in UI | V7 uses `UPDATE ... SET TEAM_ID = (subquery)` — atomic, no row filtering. Verify with `COUNT(*)` post-migration. |
| USER_TEAM constraint violation | User can't be added to team | Check constraint on ROLE column is strict (`ADMIN`, `MEMBER`, `VIEWER`). Application must send valid values. |
| Concurrent team switch + job save | Job saved to wrong team if team context changes mid-request | Backend should read team from request header/JWT at request time, not from session state. |

### UI/UX Failures

| Scenario | Impact | Mitigation |
|----------|--------|-----------|
| Schema API returns slower than timeout | Step form shows blank or error | Add retry button + loading skeleton in StepFormDialog |
| Dark mode on high-DPI display | Text blur, contrast issues | Test on Retina/4K displays during T6 audit |
| Team switch reload loses scroll position | User loses context after team switch | Acceptable for full reload. Soft navigation (T7) resolves this. |

### Security Failures

| Scenario | Impact | Mitigation |
|----------|--------|-----------|
| Missing server-side team scoping | User can access another team's jobs via direct API call | **Critical** — T3 addresses this. Frontend hiding is not security. |
| Credential leakage across teams | Team A sees Team B's credential values | Ensure `/api/credentials` is team-scoped. SECRET_REF validation should check team ownership. |
| Admin role escalation in USER_TEAM | User grants themselves ADMIN on any team | Only team admins or global admins can modify USER_TEAM memberships. Enforce in backend service layer. |

---

## Priority Order for Resolution

1. **E13** (user with no teams) — blocks usability, high severity
2. **E3 / E5** (removed step type) — affects data integrity of existing jobs
3. **T3** (server-side team scoping) — security-critical
4. **E9** (null enumValues) — potential runtime crash
5. **E10** (network failure handling) — reliability concern
6. **E1 / E14** (unsupported type warning, dark mode audit) — polish items
