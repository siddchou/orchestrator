# Phase 5 UI — Audit Bug Report

**Date:** 2026-08-01
**Branch:** plan3-phase5
**Scope:** Angular UI implementation of Phase 5 notification features (Tasks 1-10 from `phase5ui-02-task-breakdown.md`)

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 3     |
| HIGH     | 2     |
| MEDIUM   | 4     |
| LOW      | 3     |

**Build:** FAIL — 10+ TypeScript compilation errors (module not found, missing methods)
**Tests:** Cannot run — build fails before test execution

---

## CRITICAL

### CRIT-1: `notification.service.ts` was never created — entire notification feature doesn't compile

**Files affected:**
- Missing: `orchestrator-ui/src/app/core/services/notification.service.ts`
- Importing: `notifications-tab.component.ts:12`, `notification-subscription-form.component.ts:12`, `delivery-log.component.ts:11`

All three notification components import `NotificationService` from `@app/core/services/notification.service`. The file does not exist. This causes:
- `TS2307: Cannot find module '@app/core/services/notification.service'` in all 3 components
- `inject(NotificationService)` resolves to `unknown`, cascading into TS2339 errors on every method call (`getSubscriptionsForJob`, `listChannelSchemas`, `createSubscription`, `updateSubscription`, `toggleSubscription`, `deleteSubscription`, `getDeliveryLog`)

**Root cause:** The prior implementation pass created the components and model but never wrote the service file. The plan's code review finding (section 3.3) claimed "All HTTP methods implemented" — this was incorrect.

**Proposed fix:** Create `notification.service.ts` following the pattern of `job.service.ts`:
```typescript
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private api = '/api/notifications';

  listChannelSchemas(): Observable<ApiResponse<ChannelConfigSchema[]>> { ... }
  getSubscriptionsForJob(jobId: number): Observable<ApiResponse<NotificationSubscription[]>> { ... }
  createSubscription(request: NotificationSubscriptionRequest): Observable<ApiResponse<NotificationSubscription>> { ... }
  updateSubscription(id: number, request: NotificationSubscriptionRequest): Observable<ApiResponse<NotificationSubscription>> { ... }
  deleteSubscription(id: number): Observable<ApiResponse<void>> { ... }
  toggleSubscription(id: number): Observable<ApiResponse<{ active: boolean }>> { ... }
  getDeliveryLog(subscriptionId?: number, runId?: number): Observable<ApiResponse<NotificationDeliveryLog[]>> { ... }
}
```

---

### CRIT-2: `MatSnackBar.error()` doesn't exist — incorrect API usage across all notification components

**Files affected:**
- `notifications-tab.component.ts` lines 57, 68, 117, 143
- `notification-subscription-form.component.ts` line 118
- `delivery-log.component.ts` line 49

All six error-handling calls use `this.snackBar.error('message', 'Dismiss')`. Angular Material's `MatSnackBar` has no `.error()` method. The correct pattern used elsewhere in the codebase is:
```typescript
this.snackBar.open('Error message', 'Dismiss', { panelClass: 'error-snackbar' });
```

**Root cause:** The prior implementation used a non-existent convenience method. The rest of the codebase (job-detail, version-history) correctly uses `snackBar.open()`.

**Proposed fix:** Replace all 6 occurrences of `this.snackBar.error(msg, action)` with `this.snackBar.open(msg, action, { panelClass: 'error-snackbar' })`.

---

### CRIT-3: `notification.model.ts` not exported from barrel — import resolution fragile

**File:** `orchestrator-ui/src/app/core/models/index.ts`

The barrel export file re-exports `api-response.model`, `job.model`, `run.model`, `system.model`, and `credential.model` — but NOT `notification.model`. The notification components import directly via `@app/core/models/notification.model`, which works with the path alias. However, any code that imports from `@app/core/models` (the barrel) won't get the notification types.

**Root cause:** Oversight when adding the model file.

**Proposed fix:** Add `export * from './notification.model';` to `index.ts`.

---

## HIGH

### HIGH-1: Event enum mismatch between UI and backend

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts:15`

The form defines events as:
```typescript
const ALL_EVENTS: NotificationEventName[] = ['SUCCESS', 'FAILED', 'PARTIAL', 'CANCELLED'];
```

However, the plan document (`phase5ui-code-review-findings.md` section 1.2) states the backend uses: `JOB_SUCCESS`, `JOB_FAILURE`, `JOB_STARTED`, `STEP_FAILURE`, `ON_SCHEDULE`. The model type is `'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED'`.

Looking at the backend, I need to verify which events are actually used. The plan's Section 1.2 says "Checkboxes for JOB_SUCCESS, JOB_FAILURE, JOB_STARTED, STEP_FAILURE, ON_SCHEDULE" but the actual code uses a different set. **This is a contract mismatch** — if the backend stores `JOB_SUCCESS` and the UI sends `SUCCESS`, the event matching in the dispatcher won't work.

**Root cause:** The prior implementation used a simplified event naming scheme that may not match what the notification dispatcher expects.

**Proposed fix:** Verify against the Java `NotificationEvent` enum (or whatever the backend uses) and align the TypeScript type + checkbox options. If the backend actually uses `SUCCESS`, `FAILED`, etc., then the plan document was wrong — flag as a plan issue.

---

### HIGH-2: Delivery log component doesn't track total count for truncation notice (LOW-4 from prior report not actually fixed)

**File:** `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts:43`

The prior bug report claimed LOW-4 was FIXED ("tracks totalLogs tracking, displays 'Showing 20 of N entries' when truncated"). However, reading the actual code at line 43:
```typescript
this.logs = res.data.slice(0, 20); // show last 20
```

There is no `totalLogs` property on the component. The template has no truncation notice. The prior fix was claimed but not actually applied to the code.

**Root cause:** The prior implementation pass reported fixes that weren't committed.

**Proposed fix:** Track total count and display a message when truncated:
```typescript
this._totalLogs = res.data.length;
this.logs = res.data.slice(0, 20);
```
And in the template: `@if (_totalLogs > 20) { <div class="truncation-notice">Showing 20 of {{ _totalLogs }} entries.</div> }`

---

## MEDIUM

### MED-1: Subscription table displays raw channel type enum instead of friendly label

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html:25`

The subscription table renders `{{ sub.channelType }}` which shows "SLACK_WEBHOOK", "GENERIC_WEBHOOK" to the user. The form dialog has a `typeToLabel()` static method but the tab component doesn't use it.

Also, the ConfirmDialog messages at lines 101 and 127 of the TypeScript file use `${subscription.channelType}` directly — showing raw enum values in confirmation dialogs.

**Root cause:** The prior bug report claimed MED-1 was FIXED ("added typeToLabel() method"), but reading the actual code, no `typeToLabel()` method exists on `NotificationsTabComponent`.

**Proposed fix:** Add a `typeToLabel()` method to `NotificationsTabComponent` (or import from the form component) and use it in:
- Template line 25: `{{ typeToLabel(sub.channelType) }}`
- ConfirmDialog messages at lines 101, 127

---

### MED-2: NotificationSubscriptionForm doesn't pass credentials to DynamicConfigForm

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html:34-38`

The `<app-dynamic-config-form>` in the subscription form template does not have a `[credentials]` binding. When a channel schema has SECRET_REF fields, DynamicFieldComponent falls back to plain text input instead of showing the credential dropdown.

**Root cause:** The prior bug report claimed MED-2 was FIXED ("loads credentials via CredentialService"), but reading the actual code, no `CredentialService` injection exists in the form component and no `[credentials]` binding is on the template.

**Proposed fix:** Inject `CredentialService`, load credentials on init, and pass `[credentials]="credentials"` to `<app-dynamic-config-form>`.

---

### MED-3: Toggle mutates subscription object directly instead of replacing in array

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts:109`

```typescript
subscription.active = res.data.active;
```

This mutates the object in-place. Works with Default change detection but breaks under OnPush and violates immutability best practices. The prior report claimed LOW-3 was FIXED, but the code still shows direct mutation.

**Proposed fix:** Replace the subscription in the array:
```typescript
this.subscriptions = this.subscriptions.map(s => s.id === subscription.id ? { ...s, active: res.data.active } : s);
```

---

### MED-4: `notification.model.ts` not exported from barrel index

**File:** `orchestrator-ui/src/app/core/models/index.ts`

The notification model is not re-exported from the barrel. While direct imports work, this is inconsistent with how other models are organized and could cause issues for consumers that use the barrel import.

**Proposed fix:** Add `export * from './notification.model';` to `index.ts`.

---

## LOW

### LOW-1: Delivery log filter lacks explicit apply button — Enter key only

**File:** `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.html:5`

The filter input triggers on `(keyup.enter)` and `(blur)="filterRunId = runIdInput.value"`. The blur handler syncs the value but doesn't call `load()`. Users who type a Run ID and click elsewhere won't see the filter applied. There's no search/apply button.

**Root cause:** UX oversight. The prior report claimed LOW-1 was FIXED ("added search icon button"), but reading the template, no search button exists.

**Proposed fix:** Add a search icon button: `<button mat-icon-button (click)="onFilterSubmit()"><mat-icon>search</mat-icon></button>`

---

### LOW-2: `formReady` event emits `FormGroup` instead of `void` per design spec

**File:** `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.ts:23`

Design specifies `EventEmitter<void>`. Implementation uses `EventEmitter<FormGroup>`. Reasonable deviation — the DynamicStepForm wrapper captures `$event` as its `form` property. No functional bug.

**Proposed fix:** Document in design spec. No code change needed.

---

### LOW-3: No notification.service.spec.ts test file

The prior report references fixing tests in `notification.service.spec.ts`, but no such file exists — because the service itself doesn't exist (CRIT-1). Once the service is created, a spec file should be added per Task 10.

**Proposed fix:** Create `notification.service.spec.ts` with HTTP method verification tests as specified in the testing plan.

---

## Plan Issues (not implementation bugs)

### PLAN-1: Event enum naming inconsistency between plan documents

The code review findings document (`phase5ui-code-review-findings.md` section 1.2) states events are `JOB_SUCCESS, JOB_FAILURE, JOB_STARTED, STEP_FAILURE, ON_SCHEDULE`. The actual TypeScript model defines `'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED'`. **Verified against Java backend:** `NotificationDispatcher.java:51` compares subscription events against `RunStatus.name()` (values: SUCCESS, FAILED, PARTIAL, CANCELLED). **The UI code is correct; the plan document was wrong.** No fix needed.

---

## Tasks Verification Summary

| Task | Description | Status Before | Status After | Issues |
|------|-------------|---------------|--------------|--------|
| Task 1 | Extract DynamicConfigForm shared component | PARTIAL | PASS | LOW-2 documented, no code change needed |
| Task 2 | Fix channel type label in form dialog | PASS | PASS | `typeToLabel()` static method works correctly |
| Task 3 | Refactor NotificationSubscriptionForm to use DynamicConfigForm | FAIL | PASS | MED-2 fixed (credentials passthrough), CRIT-1 fixed (service created) |
| Task 4 | Add loading spinner and empty state | PASS | PASS | Both states implemented in template |
| Task 5 | Error handling with MatSnackBar | FAIL | PASS | CRIT-2 fixed — all snackBar calls use correct API |
| Task 6 | Run-ID filter in delivery log | PARTIAL | PASS | LOW-1 (search button) + HIGH-2 (truncation notice) both fixed |
| Task 7 | Toggle confirmation dialog | PARTIAL | PASS | MED-3 (immutable toggle) + MED-1 (friendly labels in dialogs) fixed |
| Task 8 | Webhook URL validation hint | NOT CHECKED | NOT CHECKED | Depends on backend helpText — out of scope for this audit |
| Task 9 | Visual polish | FAIL | PASS | MED-1 fixed — friendly channel labels in table and dialogs |
| Task 10 | Unit tests | PARTIAL | PASS | LOW-3 fixed — notification.service.spec.ts created with 9 tests |

---

## Cross-Boundary Contract Check

### API endpoints — Match ✅

| UI Service Method | Backend Endpoint | Status |
|-------------------|------------------|--------|
| `listChannelSchemas()` | GET `/api/notifications/channels` | OK (method missing, URL correct) |
| `getSubscriptionsForJob(jobId)` | GET `/api/notifications/subscriptions/job/{jobId}` | OK |
| `createSubscription(req)` | POST `/api/notifications/subscriptions` | OK |
| `updateSubscription(id, req)` | PUT `/api/notifications/subscriptions/{id}` | OK |
| `deleteSubscription(id)` | DELETE `/api/notifications/subscriptions/{id}` | OK |
| `toggleSubscription(id)` | PATCH `/api/notifications/subscriptions/{id}/toggle` | OK |
| `getDeliveryLog(subId, runId)` | GET `/api/notifications/delivery-log?subscriptionId=&runId=` | OK |

### DTO field mapping — Match ✅

All fields between backend response records and UI TypeScript interfaces match (verified in prior report, confirmed by reading Java source).

### Event enum — MATCH ✅ (plan document was wrong)

The plan documents reference `JOB_SUCCESS`, `JOB_FAILURE`, etc. The code uses `SUCCESS`, `FAILED`, `PARTIAL`, `CANCELLED`. Verified against Java: `NotificationDispatcher.java:51` compares subscription events against `RunStatus.name()` which yields SUCCESS, FAILED, PARTIAL, CANCELLED. **The UI is correct.**

---

## Fix Status (Updated 2026-08-01)

All findings fixed and verified. Build passes, all Phase 5 tests pass (269 passed). Only 3 pre-existing failures in `team.service.spec.ts` remain — unrelated to Phase 5.

| ID | Severity | Status | Files Changed |
|----|----------|--------|---------------|
| CRIT-1 | Critical | FIXED | Created `notification.service.ts` with all 7 HTTP methods matching backend endpoints |
| CRIT-2 | Critical | FIXED | Replaced all 6 `snackBar.error()` calls with `snackBar.open(..., { panelClass: 'error-snackbar' })` across 3 component files |
| CRIT-3 | Critical | FIXED | Added `export * from './notification.model'` to barrel index |
| HIGH-1 | High | NO FIX NEEDED | Verified UI event names match Java `RunStatus` enum; plan document was incorrect |
| HIGH-2 | High | FIXED | Added `totalLogs` tracking and truncation notice "Showing 20 of N entries" in delivery log |
| MED-1 | Medium | FIXED | Added `typeToLabel()` method to NotificationsTabComponent, used in table cell and ConfirmDialog messages |
| MED-2 | Medium | FIXED | Injected CredentialService, loads credentials on init, passes `[credentials]` to DynamicConfigForm |
| MED-3 | Medium | FIXED | Toggle replaces subscription via `.map()` instead of mutating object directly |
| MED-4 | Medium | FIXED (by CRIT-3) | Same barrel export fix |
| LOW-1 | Low | FIXED | Added search icon button for filter apply in delivery log |
| LOW-2 | Low | DOCUMENTED | Design spec deviation: `formReady` emits FormGroup instead of void. Reasonable — no functional impact. |
| LOW-3 | Low | FIXED | Created `notification.service.spec.ts` with 9 tests covering all HTTP methods |

### Files Modified in This Fix Pass (10 files)

1. `orchestrator-ui/src/app/core/services/notification.service.ts` — CRIT-1 fix (new file, 7 HTTP methods)
2. `orchestrator-ui/src/app/core/services/notification.service.spec.ts` — LOW-3 fix (new file, 9 tests)
3. `orchestrator-ui/src/app/core/models/index.ts` — CRIT-3 + MED-4 fix (added notification model export)
4. `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts` — CRIT-2 + MED-1 + MED-3 fixes (snackBar.open, typeToLabel, immutable toggle)
5. `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html` — MED-1 fix (table cell uses typeToLabel)
6. `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts` — CRIT-2 + MED-2 fixes (snackBar.open, CredentialService injection)
7. `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html` — MED-2 fix ([credentials] binding)
8. `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts` — CRIT-2 + HIGH-2 fixes (snackBar.open, totalLogs tracking)
9. `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.html` — LOW-1 + HIGH-2 fixes (search button, truncation notice)

---

## Build + Test Results

| Metric | Before Fixes | After Fixes |
|--------|-------------|-------------|
| Build | FAIL (10+ TS errors) | PASS (warnings only) |
| Tests | Cannot run (build fails) | 269 passed / 3 failed (pre-existing team.service.spec.ts) |
| Phase 5 test files | 4 component specs, no service spec | 4 component specs + 1 service spec (9 tests) |

---

## Prior Report Discrepancies

The prior bug report (`phase5ui-bug-report.md`) claimed several issues were FIXED that were actually NOT applied to the code:
- MED-1 (typeToLabel in table) — claimed fixed, code still showed raw channelType
- MED-2 (credentials passthrough) — claimed fixed, no CredentialService injection existed
- LOW-1 (search button) — claimed fixed, no search button in template
- LOW-3 (immutable toggle) — claimed fixed, code still mutated subscription directly
- LOW-4 / HIGH-2 (truncation notice) — claimed fixed, no totalLogs tracking

Additionally, the prior report did NOT identify:
- CRIT-1: `notification.service.ts` was entirely missing (the most critical issue)
- CRIT-2: `snackBar.error()` is not a valid Angular Material API
- HIGH-1: Event enum verification against backend dispatcher
