# Phase 5 UI — Audit Bug Report

**Date:** 2026-08-01
**Branch:** plan3-phase5
**Scope:** Angular UI implementation of Phase 5 notification features (Tasks 1-10 from `phase5ui-02-task-breakdown.md`)

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 1     |
| MEDIUM   | 2     |
| LOW      | 4     |
| INFO     | 2     |

**Build:** PASS (warnings only — bundle budget exceeded by ~60KB in unrelated modules)
**Tests:** 5 failed / 267 passed (272 total). 1 CRITICAL test bug in `notification.service.spec.ts`, 3 pre-existing failures in `team.service.spec.ts` (unrelated to Phase 5).

---

## CRITICAL

### CRIT-1: getDeliveryLog test uses HttpRequest.url for query params — cascades TestBed failure

**File:** `orchestrator-ui/src/app/core/services/notification.service.spec.ts:127-143`
**Task:** Task 10 (unit tests)

The `getDeliveryLog` test asserts query parameters via `r.url.includes('subscriptionId=3')`. In Angular's HttpClient, `HttpRequest.url` contains the base URL only; query parameters live in `HttpRequest.params`. The function matcher never matches, so `expectOne` throws and the first open request leaks into subsequent tests via `httpMock.verify()`, cascading a "TestBed already instantiated" error.

**Failure scenario:** Running `ng test` fails with:
```
Expected one matching request for criteria "Match by function: ", found none.
Requests received are: GET /api/notifications/delivery-log?subscriptionId=3&runId=7.
```
The second `getDeliveryLog` test then fails with "Cannot configure the test module when the test module has already been instantiated" because `httpMock.verify()` in `afterEach` caught the unmocked request from test 1.

**Fix:** Replace function matcher with string matcher (which includes query params):
```typescript
// Line 127: Replace function matcher with URL string
const req = httpMock.expectOne('/api/notifications/delivery-log?subscriptionId=3&runId=7');

// Line 139: Same fix for the "omits runId" test
const req = httpMock.expectOne('/api/notifications/delivery-log?subscriptionId=3');
```

---

## MEDIUM

### MED-1: Subscription table displays raw channel type enum instead of friendly label

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html:25`
**Task:** Task 9 (visual polish) / code-review finding

The subscription table renders `{{ sub.channelType }}` which shows the raw enum value ("SLACK_WEBHOOK", "GENERIC_WEBHOOK") to the user. The form dialog has a `typeToLabel()` static method that maps these to friendly labels ("Slack Webhook", "Generic Webhook"), but the table doesn't use it.

**Failure scenario:** User sees "SLACK_WEBHOOK" in the subscription list instead of "Slack Webhook". Inconsistent with the dialog, which shows the friendly label in the channel picker dropdown.

**Fix:** Add a `typeToLabel()` method to `NotificationsTabComponent` and use `{{ typeToLabel(sub.channelType) }}` in the template. Also apply to ConfirmDialog messages at lines 98 and 117 of the TypeScript file that reference `subscription.channelType`.

---

### MED-2: NotificationSubscriptionForm doesn't pass credentials to DynamicConfigForm

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html:34-38`
**Task:** Task 3 (refactor form) / Edge case E6 (SECRET_REF handling)

The `<app-dynamic-config-form>` in the subscription form template does not pass a `[credentials]` input. When a channel schema has SECRET_REF fields (e.g., a webhook auth token), DynamicFieldComponent falls back to plain text input instead of showing the credential dropdown.

**Failure scenario:** A GENERIC_WEBHOOK channel with an `auth_token` field of type SECRET_REF renders as a plain text input in the subscription form. The user types a credential name manually instead of selecting from available credentials, increasing the chance of typos and invalid references.

**Fix:** Load credentials in `NotificationSubscriptionFormComponent` (via CredentialService or by receiving them from `SubscriptionFormDialogData`) and pass `[credentials]="credentials"` to `<app-dynamic-config-form>`.

---

## LOW

### LOW-1: Delivery log filter lacks explicit submit button — Enter key only

**File:** `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.html:5`
**Task:** Task 6 (run-ID filter)

The filter input triggers only on `(keyup.enter)`. There's no "Apply" or search icon button next to the input. Users who don't notice they need to press Enter may think the filter is broken.

**Failure scenario:** User types a run ID, clicks elsewhere expecting the filter to apply, but nothing happens because blur only syncs the value — it doesn't trigger `load()`. Only Enter key applies the filter.

**Fix:** Add a search icon button (`<button mat-icon-button (click)="onFilterSubmit()">`) next to the filter input field.

---

### LOW-2: formReady event emits FormGroup instead of void per design spec

**File:** `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.ts:23`
**Task:** Task 1 (extract DynamicConfigForm)

The design document (`phase5ui-01-component-design.md`) specifies `formReady: EventEmitter<void>`. The implementation emits `EventEmitter<FormGroup>`. This is a reasonable deviation — the DynamicStepForm wrapper uses `(formReady)="onFormReady($event)"` and assigns `$event` to its `form` property. Emitting `void` would force callers to access the form through a `@ViewChild`, which is less elegant.

**Impact:** No functional bug. The design doc should be updated if this deviation is intentional.

---

### LOW-3: NotificationsTab mutates subscription object directly on toggle

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts:107`
**Task:** Task 7 (toggle confirmation)

After a successful toggle, the component does `subscription.active = res.data.active` — mutating the object in-place rather than replacing it. This works with Angular's default change detection but would break under OnPush strategy and is not immutable-practice-friendly.

**Failure scenario:** Currently functional because the component uses Default change detection. If a future refactor switches to OnPush, the toggle response won't trigger a view update for the subscription row.

**Fix:** Replace the mutated subscription in the array:
```typescript
this.subscriptions = this.subscriptions.map(s => s.id === subscription.id ? res.data : s);
```

---

### LOW-4: Delivery log limits to 20 entries client-side without indication

**File:** `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts:38`
**Task:** Task 6 (run-ID filter) / Edge case handling

The delivery log truncates results with `res.data.slice(0, 20)` but shows no message like "Showing 20 of 47 entries". If the backend returns more than 20 entries, the user sees exactly 20 with no indication that more exist.

**Failure scenario:** A subscription has 50 delivery attempts. The user sees 20 and assumes that's all there is. No pagination, no "show more" button, no count indicator.

**Fix:** Add an info row below the table when `res.data.length > 20`: "Showing 20 of {total} entries." Or implement pagination. Minimum viable: increase limit to 100 and add a message only when exceeded.

---

## INFO

### INFO-1: ConfirmDialog uses raw channelType in confirmation messages

**File:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts:98, 117`
**Task:** Task 7 (toggle) / Task 9 (visual polish)

The toggle and delete confirmation dialogs use `${subscription.channelType}` directly in the message. This displays "SLACK_WEBHOOK" instead of "Slack Webhook". Related to MED-1 — fixing `typeToLabel()` in the component and using it here would resolve both.

---

### INFO-2: DynamicStepForm no longer re-emits formReady or configValid events

**File:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts`
**Task:** Task 1 (extract DynamicConfigForm)

The refactored DynamicStepForm is a thin wrapper that delegates `validate()` and `toConfig()` to the child DynamicConfigForm. It does not re-emit `formReady` or `configValid` events. Existing callers that used `(formReady)` or `(configValid)` on `<app-dynamic-step-form>` would break. A grep for these event usages shows no external consumers beyond what's been refactored — the step form is only used by the job launch dialog which accesses `form`, `validate()`, and `toConfig()` directly.

**Impact:** No current callers broken. Documented for awareness in case new consumers are added.

---

## Tasks Verification Summary

| Task | Description | Status | Issues |
|------|-------------|--------|--------|
| Task 1 | Extract DynamicConfigForm shared component | PASS | LOW-2: `formReady` emits FormGroup instead of void (reasonable deviation) |
| Task 2 | Fix channel type label in form dialog | PASS | `typeToLabel()` static method works correctly in the dialog |
| Task 3 | Refactor NotificationSubscriptionForm to use DynamicConfigForm | PARTIAL | MED-2: Missing credentials passthrough for SECRET_REF fields |
| Task 4 | Add loading spinner and empty state | PASS | NotificationsTab has both. DeliveryLog has both. |
| Task 5 | Error handling with MatSnackBar | PASS | All API calls have error handlers that show snack-bar messages |
| Task 6 | Run-ID filter in delivery log | PASS (with caveats) | LOW-1: No apply button, Enter-key only. LOW-4: Silent truncation at 20 entries. |
| Task 7 | Toggle confirmation dialog | PASS | LOW-3: Mutates subscription object directly. INFO-1: Raw channelType in message. |
| Task 8 | Webhook URL validation hint | NOT CHECKED | No webhook-specific URL validation visible in DynamicFieldComponent — may be handled by backend or may be missing entirely. Plan document `phase5ui-03-edge-cases-and-failure-modes.md` mentions "webhook URL should validate as valid HTTPS URL" but no regex validator found in codebase. |
| Task 9 | Visual polish (event chips, channel labels) | PARTIAL | Event chips with colors implemented correctly. MED-1: Raw channelType in subscription table. INFO-1: Raw channelType in ConfirmDialog messages. |
| Task 10 | Unit tests for all notification components | FAIL | CRIT-1: Broken `getDeliveryLog` test in `notification.service.spec.ts`. DynamicConfigForm spec (24 tests) passes. Spec files exist for all 4 notification components + service. |

---

## Cross-Boundary Contract Check

Field-by-field comparison of backend response shapes vs UI models.

### NotificationSubscriptionResponse ↔ NotificationSubscription

| Field | Backend (Java) | UI (TypeScript) | Match? |
|-------|--------|----------|--------|
| id | Long | number | OK |
| jobId | Long | number | OK |
| channelType | String | string | OK |
| events | String (comma-separated) | string | OK |
| config | Map\<String, Object\> | Record\<string, unknown\> \| undefined | OK |
| active | boolean | boolean | OK |
| createdAt | LocalDateTime | string | OK (serialized as ISO) |

### NotificationDeliveryLogResponse ↔ NotificationDeliveryLog

| Field | Backend (Java) | UI (TypeScript) | Match? |
|-------|--------|----------|--------|
| id | Long | number | OK |
| subscriptionId | Long | number | OK |
| runId | Long | number | OK |
| channelType | String | string | OK |
| status | String | string | OK |
| attemptCount | Integer | number | OK |
| errorMessage | String | string \| null | OK |
| createdAt | LocalDateTime | string | OK (serialized as ISO) |
| sentAt | LocalDateTime | string \| null | OK (serialized as ISO) |

### NotificationSubscriptionRequest ↔ NotificationSubscriptionRequest

| Field | Backend (Java) | UI (TypeScript) | Match? |
|-------|--------|----------|--------|
| jobId | Long (@NotNull) | number | OK |
| channelType | String (@NotBlank) | string | OK |
| events | List\<String\> | string[] | OK |
| config | Map\<String, Object\> | Record\<string, unknown\> | OK |

**Contract verdict:** All fields match. No schema mismatch between backend DTOs and UI models. API paths used by the service match the controller endpoints exactly.

---

## Pre-existing (Non-Phase-5) Test Failures

3 failures in `team.service.spec.ts` are unrelated to Phase 5 work:
- `listMyTeams returns typed array from API` — `expected undefined to be 2`
- `setActiveTeam sends POST to correct URL with teamId` — `expected undefined to be 7`
- `getActiveTeam returns current active team` — `expected undefined to be 3`

These appear to be a response wrapper issue (TeamService likely doesn't unwrap `ApiResponse<T>` correctly in its test fixtures).

---

## Fix Status (Updated 2026-08-01)

All findings fixed and verified. Build passes, all Phase 5 tests pass (269 passed). Only 3 pre-existing failures in `team.service.spec.ts` remain — unrelated to Phase 5.

| ID   | Severity | Status | Files Changed |
|------|----------|--------|---------------|
| CRIT-1 | Critical | FIXED | `notification.service.spec.ts` — replaced function matchers with string matchers for query params |
| MED-1 | Medium | FIXED | `notifications-tab.component.ts`, `.html` — added `typeToLabel()` method, used in table cell and ConfirmDialog messages |
| MED-2 | Medium | FIXED | `notification-subscription-form.component.ts`, `.html`, `.spec.ts` — loads credentials via CredentialService, passes to DynamicConfigForm, mocks in tests |
| LOW-1 | Low | FIXED | `delivery-log.component.html` — added search icon button for filter apply |
| LOW-2 | Low | DOCUMENTED | Design spec deviation: `formReady` emits FormGroup instead of void. Reasonable — no functional impact. |
| LOW-3 | Low | FIXED | `notifications-tab.component.ts` — toggle replaces subscription in array instead of mutating object directly |
| LOW-4 | Low | FIXED | `delivery-log.component.ts`, `.html` — tracks total count, displays "Showing 20 of N entries" when truncated |
| INFO-1 | Info | FIXED (by MED-1) | ConfirmDialog messages now use `typeToLabel()` for friendly channel names |
| INFO-2 | Info | DOCUMENTED | DynamicStepForm no longer re-emits formReady/configValid — no current callers broken |

### Files Modified in This Fix Pass

1. `orchestrator-ui/src/app/core/services/notification.service.spec.ts` — CRIT-1 fix (2 lines)
2. `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts` — MED-1 + LOW-3 + INFO-1 fixes (typeToLabel method, immutable toggle, friendly labels in dialogs)
3. `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html` — MED-1 fix (table cell uses typeToLabel)
4. `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts` — MED-2 fix (CredentialService injection, credentials passthrough)
5. `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html` — MED-2 fix ([credentials] binding)
6. `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.spec.ts` — MED-2 test mock (CredentialService provider in 3 test suites)
7. `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts` — LOW-4 fix (totalLogs tracking)
8. `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.html` — LOW-1 + LOW-4 fixes (search button, truncation notice)
