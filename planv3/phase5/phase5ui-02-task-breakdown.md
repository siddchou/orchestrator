# Phase 5 UI — Task Breakdown

## Task 1: Extract DynamicConfigFormComponent from DynamicStepFormComponent

**Files:**
- NEW: `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.ts`
- NEW: `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.html`
- NEW: `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.scss`
- MOD: `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts` (become wrapper)

**Work:**
1. Copy the form-building logic from DynamicStepFormComponent into a new standalone component that accepts `fields: FieldDefinition[]` as input instead of `schema: StepConfigSchema`
2. Move `buildForm()`, `fieldValidators()`, `resolveInitialValue()`, `toConfig()`, `validate()` to the new component
3. Refactor DynamicStepFormComponent to wrap DynamicConfigFormComponent, passing through `schema.fields`
4. Verify Phase 2 step config forms still work (regression check)

**DoD:**
- [ ] DynamicConfigFormComponent renders fields from a FieldDefinition[] input
- [ ] DynamicStepFormComponent delegates to DynamicConfigFormComponent
- [ ] Existing step configuration dialogs render correctly (visual regression check)
- [ ] Form validation behavior is preserved (required, SECRET_REF, LIST_STRING)

---

## Task 2: Fix channel label bug in NotificationSubscriptionFormComponent

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts`

**Work:**
1. Add `typeToLabel()` static method to convert channel type codes to display names
2. Fix the `open()` method's channel options mapping: `label: NotificationSubscriptionFormComponent.typeToLabel(schema.type)`
3. Verify dropdown displays "Email", "Slack Webhook", "Generic Webhook"

**DoD:**
- [ ] Channel picker shows human-readable labels
- [ ] Selecting a channel still populates config fields correctly
- [ ] No regression in create/edit flow

---

## Task 3: Refactor NotificationSubscriptionForm to use DynamicConfigFormComponent

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts`
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html`

**Work:**
1. Remove manual `buildConfigFields()`, manual FormGroup construction, and manual validation logic
2. Embed `<app-dynamic-config-form>` in template with `[fields]="selectedSchema.fields"` and `[existingConfig]` binding
3. Listen to `(formReady)` and `(configValid)` events instead of manual validation
4. Remove `invalidConfigFields` Set and associated template logic
5. Update submit handler to use config from DynamicConfigFormComponent's event

**DoD:**
- [ ] Config fields render correctly for all three channel types
- [ ] Required field validation works (red border + error text)
- [ ] LIST_STRING fields render as chip grids
- [ ] ENUM fields render as dropdowns with correct options
- [ ] Edit mode prepopulates existing config values
- [ ] Submit sends correctly structured `NotificationSubscriptionRequest`

---

## Task 4: Add empty state and loading state to NotificationsTabComponent

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html`
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.scss`

**Work:**
1. Add empty state div with message and "Create Subscription" CTA button when subscriptions array is empty and loading is false
2. Add loading spinner overlay on table during initial data load
3. Style empty state to match the delivery log's existing `.empty-state` pattern (centered, muted color)

**DoD:**
- [ ] Empty state displays when no subscriptions exist
- [ ] CTA button opens the create dialog
- [ ] Loading spinner shows during `getSubscriptionsForJob()` call
- [ ] Table displays normally when subscriptions exist

---

## Task 5: Add error handling with MatSnackBar to NotificationsTabComponent

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts`

**Work:**
1. Inject `MatSnackBar` into component
2. Add `.pipe(catchError((error) => { this.snackBar.error(...); return EMPTY; }))` to all HTTP calls (load, create, edit, toggle, delete)
3. Display meaningful error messages: "Failed to load subscriptions", "Failed to create subscription", etc.

**DoD:**
- [ ] HTTP errors display toast notifications
- [ ] Component doesn't crash on 500 responses
- [ ] Loading state resets after error (no stuck spinner)

---

## Task 6: Add run-ID filter to DeliveryLogComponent

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts`
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.html`
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.scss`

**Work:**
1. Add `filterRunId` signal/input to component
2. Add run ID input field above the table with clear button
3. Pass `runId` query parameter to `getDeliveryLog()` when filter is set
4. Style the filter input to match existing form field patterns

**DoD:**
- [ ] Run ID filter input appears above the delivery log table
- [ ] Entering a run ID and pressing Enter filters the log
- [ ] Clear button removes the filter and reloads all deliveries
- [ ] Filter works with backend's `?runId=` parameter

---

## Task 7: Add confirmation dialog for toggle active/inactive

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts`
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.html`

**Work:**
1. Replace the immediate `toggleActive()` call with a confirmation dialog
2. Use the existing pattern from `confirmDeleteSubscription()` — inline template dialog
3. Message: "Disable this subscription?" / "Re-enable this subscription?" depending on current state

**DoD:**
- [ ] Toggle action shows confirmation dialog
- [ ] Dialog message reflects the action (enable vs disable)
- [ ] Cancel closes dialog with no side effects
- [ ] Confirm toggles the subscription and refreshes the list

---

## Task 8: Polish — webhook URL validation hint

**Files:**
- MOD: `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.html` (or handled via DynamicFieldComponent help text)

**Work:**
1. Add `helpText` to webhook URL fields in the channel schemas so users know the expected format. This is a backend change — ensure `SlackWebhookChannel` and `GenericWebhookChannel` field definitions include helpful `helpText`:
   - Slack: "Incoming Webhook URL from your Slack app settings (https://hooks.slack.com/...)"
   - Generic: "Full URL for the webhook endpoint (https://...)"
2. If backend helpText is already present, verify it displays in the form field hint area via DynamicFieldComponent

**DoD:**
- [ ] Webhook URL fields show helpful placeholder text or hint
- [ ] Help text is sourced from backend FieldDefinition.helpText
- [ ] Displays as mat-form-field hint text

---

## Task 9: Visual polish and consistency pass

**Files:**
- MOD: SCSS files for all three notification components

**Work:**
1. Ensure spacing, typography, and colors match the rest of the job detail page tabs
2. Check that status chips in subscription table and delivery log use consistent color semantics
3. Verify responsive behavior — does the subscription table scroll horizontally on narrow viewports?
4. Check that dialog max-width is appropriate for config forms with multiple fields

**DoD:**
- [ ] Visual consistency with other job detail tabs confirmed
- [ ] Status chip colors are consistent across components
- [ ] Table scrolls horizontally on narrow screens without breaking layout
- [ ] Dialog fits comfortably on 1280px and 1920px viewports

---

## Task 10: Unit tests for notification components

**Files:**
- NEW: `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.spec.ts`
- NEW: `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.spec.ts`
- NEW: `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.spec.ts`
- NEW: `orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.spec.ts`

**Work:** See `phase5ui-04-testing-plan.md` for detailed test cases.

**DoD:**
- [ ] All spec files pass with `ng test`
- [ ] Coverage includes happy path, error handling, and edge cases listed in testing plan
- [ ] DynamicConfigFormComponent tests verify field type rendering and validation

---

## Task Grouping (PRs)

| PR | Tasks | Description |
|----|-------|-------------|
| PR 1 | Task 1, 2, 3 | Extract DynamicConfigForm + refactor notification form |
| PR 2 | Task 4, 5, 6, 7, 8 | UX polish: empty states, errors, run filter, confirmations |
| PR 3 | Task 9, 10 | Visual polish + tests |
