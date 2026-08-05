# Phase 5 UI — Testing Plan

## Unit Tests

### DynamicConfigFormComponent (`dynamic-config-form.spec.ts`)

| Test | Assertion |
|------|-----------|
| Creates form controls from FieldDefinition[] | Given 3 fields, `form.controls` has 3 entries keyed by field name |
| STRING required field applies RequiredValidator | Control has `Validators.required` in validators array |
| STRING optional field has no required validator | Control lacks `Validators.required` |
| NUMBER field initializes with null for optional, defaultValue for specified | `control.value === null` or `control.value === 42` |
| BOOLEAN field defaults to false | `control.value === false` when no defaultValue |
| ENUM field creates a control with initial value from defaultValue | `control.value === 'POST'` for method enum with default POST |
| LIST_STRING field splits comma-separated defaultValue | `"a,b,c"` → `['a', 'b', 'c']` in `resolveInitialValue` |
| SECRET_REF field validates against credential list | Invalid credential ID triggers validation error |
| `toConfig()` returns flat Record<string, unknown> | Output matches expected key-value pairs |
| `toConfig()` converts LIST_STRING array to comma-separated string | `['a', 'b']` → `"a,b"` in output |
| `validate()` marks required fields as touched and returns false on empty | Return value is `false`, controls are touched |
| `formReady` emits after buildForm | Spy calls count === 1 |
| Changing `fields` input rebuilds form | Set fields to a new array; form controls update |

### NotificationsTabComponent (`notifications-tab.component.spec.ts`)

| Test | Assertion |
|------|-----------|
| Loads subscriptions on init | `getSubscriptionsForJob` called with correct jobId |
| Loads channel schemas on init | `listChannelSchemas` called once |
| Empty state displays when no subscriptions | Template contains empty state element when subscriptions array is empty |
| Table rows match subscription count | 3 subscriptions → 3 table data rows |
| Create dialog opens and creates subscription | Click create button → dialog opens → form emits request → `createSubscription` called → list refreshes |
| Edit dialog prepopulates with subscription data | Click edit on subscription A → dialog receives subscription A in open() call |
| Toggle active sends PATCH request | Click toggle on active subscription → `toggleSubscription(id)` called → list refreshes |
| Delete shows confirmation dialog | Click delete → confirm dialog opens → click confirm → `deleteSubscription` called |
| Delete cancel does nothing | Click delete → confirm dialog opens → click cancel → no HTTP call |
| Delivery log toggles visibility | Click "Show deliveries" → `showingLogFor$` emits subscription ID → delivery-log component visible |
| Error on load shows snackbar | `getSubscriptionsForJob` returns error → `snackBar.error` called |
| Events display as chips | Subscription with events "JOB_SUCCESS,JOB_FAILURE" renders 2 mat-chips |

### NotificationSubscriptionFormComponent (`notification-subscription-form.component.spec.ts`)

| Test | Assertion |
|------|-----------|
| `typeToLabel('EMAIL')` returns 'Email' | String equality |
| `typeToLabel('SLACK_WEBHOOK')` returns 'Slack Webhook' | String equality |
| `typeToLabel('UNKNOWN_TYPE')` returns title-cased version | Fallback formatting works |
| Channel selection triggers config form build | Select EMAIL → DynamicConfigFormComponent receives email schema fields |
| No channel selected disables submit | Submit button is disabled when `selectedSchema` is null |
| Required event check prevents submit | Uncheck all events → submit is disabled or shows error |
| Config validation prevents submit with required field empty | Webhook URL empty for SLACK_WEBHOOK → submit disabled |
| Create mode emits NotificationSubscriptionRequest | Submit → dialog closes with `{ jobId, channelType, events[], config }` |
| Edit mode passes subscription data | `open()` with existing subscription → form prepopulates channel type, events, config |
| No channels available shows message | Call `open()` with empty schemas array → template shows "No notification channels configured" |

### DeliveryLogComponent (`delivery-log.component.spec.ts`)

| Test | Assertion |
|------|-----------|
| Loads delivery log on init with subscriptionId | `getDeliveryLog({ subscriptionId })` called |
| Displays entries in table rows | 5 entries → 5 data rows |
| Status chip color for SUCCESS | Element has expected CSS class/color |
| Status chip color for FAILED | Element has expected CSS class/color |
| Empty log shows empty state | 0 entries → "No delivery attempts recorded" visible |
| Run ID filter passes parameter | Set `filterRunId = 42`, call `load()` → `getDeliveryLog` called with `{ subscriptionId, runId: '42' }` |
| Clearing run ID reloads without filter | Click clear → `filterRunId` is null → `load()` called → no runId parameter |
| Null sentAt displays gracefully | Entry with `sentAt: null` → column shows "—" or empty, no error |

### NotificationService (`notification.service.spec.ts`)

| Test | Assertion |
|------|-----------|
| `listChannelSchemas` calls GET /api/notifications/channels | HTTP request URL and method correct |
| `getSubscriptionsForJob(5)` calls GET /api/notifications/subscriptions/job/5 | Path parameter correct |
| `createSubscription(req)` POSTs to /api/notifications/subscriptions | Body matches request object |
| `updateSubscription(3, req)` PUTs to /api/notifications/subscriptions/3 | Path and body correct |
| `deleteSubscription(3)` DELETEs /api/notifications/subscriptions/3 | Method and path correct |
| `toggleSubscription(3)` PATCHes /api/notifications/subscriptions/3/toggle | Method and path correct |
| `getDeliveryLog({ subscriptionId: 3, runId: 7 })` includes query params | URL contains `subscriptionId=3&runId=7` |

---

## E2E Tests

### Test Environment
- Angular dev server running with test backend (H2 database or test profile)
- Seed data: One job with no subscriptions, one job with 2 subscriptions (Email + Slack)
- Mock notification channels: EMAIL and SLACK_WEBHOOK registered

### Scenario 1: Create a Slack Webhook subscription

1. Navigate to job detail page
2. Click "Notifications" tab
3. Verify empty state is displayed (for job with no subscriptions)
4. Click "Create Subscription" button
5. In dialog, select "Slack Webhook" from channel dropdown
6. Verify config fields appear: "Webhook URL" (required), "Channel" (optional)
7. Enter `https://hooks.slack.com/services/T00/B00/xxx` in Webhook URL
8. Check "Job Failed" and "Job Succeeded" event checkboxes
9. Click "Create"
10. Verify subscription appears in table with correct channel type, events as chips, active status
11. Verify no validation errors displayed

### Scenario 2: Edit an existing subscription

1. Navigate to job detail with seeded subscriptions
2. Click edit icon on Slack subscription row
3. Verify dialog prepopulates: channel = "Slack Webhook", webhook URL filled, events checked
4. Change webhook URL to a different value
5. Uncheck one event
6. Click "Save"
7. Verify table row updates with new event count
8. Click edit again to verify values persisted

### Scenario 3: Toggle and delete subscription

1. Click toggle on active subscription
2. Verify confirmation dialog appears with "Disable this subscription?"
3. Click confirm
4. Verify row shows inactive status (chip or icon change)
5. Click toggle again to re-enable
6. Confirm and verify active status restored
7. Click delete on a subscription
8. Verify confirmation dialog shows subscription details
9. Click confirm
10. Verify row is removed from table

### Scenario 4: View delivery log with run-ID filter

1. Navigate to job with seeded subscriptions and delivery history
2. Click "Show deliveries" on a subscription row
3. Verify delivery log table appears with entries
4. Verify status chips are color-coded (green for SUCCESS, red for FAILED)
5. Enter a run ID in the filter field
6. Press Enter
7. Verify table shows only entries matching that run ID
8. Click clear button on filter
9. Verify table shows all entries again

### Scenario 5: Error handling

1. Mock backend to return 500 on `/api/notifications/subscriptions/job/{jobId}`
2. Navigate to notifications tab
3. Verify error toast appears: "Failed to load subscriptions"
4. Verify table shows empty state (not a crash)
5. Mock backend to return 500 on create subscription
6. Attempt to create a subscription
7. Verify error toast appears and dialog stays open with data preserved

### Scenario 6: Missing channel type

1. Create a subscription with EMAIL channel
2. Mock backend to remove EMAIL from `/api/notifications/channels` (simulate bean not available)
3. Refresh page
4. Click edit on EMAIL subscription
5. Verify dialog shows "Channel type no longer available" message for config section
6. Verify delete still works

---

## Regression Tests

Ensure Phase 2 step configuration is unaffected by DynamicConfigFormComponent extraction:

1. Navigate to a job with steps
2. Open step configuration for each step type
3. Verify all fields render correctly (STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING)
4. Verify validation works (required fields, credential dropdown)
5. Save config and verify no console errors
