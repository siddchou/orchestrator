# Phase 5 UI — Edge Cases and Failure Modes

## Webhook URL Validation

| Scenario | Behavior |
|----------|----------|
| User enters a non-HTTPS URL (e.g., `http://localhost:8080/hook`) | Field accepts input (backend validates on delivery). Consider adding client-side hint text via `FieldDefinition.helpText` to guide toward HTTPS. No hard validation — generic webhooks may legitimately target HTTP endpoints in internal networks. |
| User enters a malformed URL (missing scheme, spaces) | Same as above — backend will fail on delivery and the delivery log shows FAILED status. The user can then edit the subscription with the correct URL. |
| Slack webhook URL pasted into Generic Webhook channel | No issue — it's just a URL. The payload format might not match Slack's expected structure, but that's a configuration error visible in the delivery log. |

## Empty States

| Scenario | Behavior |
|----------|----------|
| No subscriptions for a job | Empty state displayed with "No notification subscriptions" message and "Create Subscription" CTA button (Task 4) |
| No channel schemas available (backend returns empty array) | Channel picker shows no options. Create dialog should display "No notification channels configured" message instead of an empty dropdown. Guard in `openCreateDialog()`: check `channelSchemas().length === 0` and show a `MatSnackBar.warning()` or inline message. |
| Delivery log is empty for a subscription | Already handled — "No delivery attempts recorded" with centered styling |

## Deleted Channel / Missing Channel Type

| Scenario | Behavior |
|----------|----------|
| Subscription references a channel type no longer registered (e.g., custom channel removed from classpath) | The channel picker won't show the type. Edit dialog: if `subscription.channelType` doesn't match any available schema, disable config field rendering and display "Channel type no longer available — config fields cannot be edited". The subscription can still be deleted or toggled. |
| Email channel not registered (no JavaMailSender bean) | Channel picker simply omits EMAIL from the dropdown. No special handling needed beyond what the backend already does. |

## Rapid Clicks / Double Submission

| Scenario | Behavior |
|----------|----------|
| User double-clicks "Create" button in subscription form | The submit button should be disabled while the HTTP request is in flight. Add a `submitting` signal to the form component; disable the button and show a spinner in its place: `<button [disabled]="submitting() || !formValid()">Create <mat-spinner *ngIf="submitting()"></mat-spinner></button>` |
| User rapidly toggles active/inactive | Same pattern — disable the toggle button during the PATCH request. The existing `loading()` signal can be reused, or a more granular `togglingSubscriptionId` tracker. |
| User opens multiple create dialogs | MatDialog with `disableClose: true` prevents stacking. Consider adding `{ disableClose: true }` to dialog config if not already present. |

## SECRET_REF Field Handling

| Scenario | Behavior |
|----------|----------|
| Notification channel adds a SECRET_REF field in the future (e.g., API key for a webhook auth) | DynamicConfigFormComponent inherits SECRET_REF handling from DynamicStepFormComponent — it renders a dropdown of available credentials. This works out of the box if `credentials` input is passed through. Currently no notification channel uses SECRET_REF, but the extraction ensures future compatibility. |
| No credentials available and field is SECRET_REF | DynamicFieldComponent shows an empty dropdown with validation error "No credentials available". This is inherited behavior from Phase 2. |

## Config Field Type Mismatches

| Scenario | Behavior |
|----------|----------|
| Backend returns a field type the UI doesn't recognize | DynamicFieldComponent's switch statement has a default case that renders nothing (or a plain text input). Add a fallback: render `mat-input` with a warning comment in console. This prevents silent field disappearance. |
| LIST_STRING field receives a string value from backend (comma-separated) vs array | DynamicConfigFormComponent's `resolveInitialValue()` should split comma-separated strings into arrays for LIST_STRING fields, matching the Phase 2 behavior already in DynamicStepFormComponent. Verify this conversion is preserved during extraction. |
| NUMBER field receives empty string | FormGroup control with `type="number"` treats empty as `null` or `NaN`. Backend Jackson deserializes accordingly. Add a validator: if required and null/NaN, show error. |

## Subscription Lifecycle

| Scenario | Behavior |
|----------|----------|
| User creates subscription, then immediately deletes the job | Backend should handle cascading delete. UI doesn't need special handling — the job detail page won't be visible after job deletion. |
| User edits a subscription while a delivery is in progress | No conflict — the dispatcher reads subscriptions at dispatch time. An edit mid-delivery affects the next dispatch, not the in-flight one. |
| Multiple users edit the same subscription concurrently | Last write wins. No optimistic locking on notification subscriptions. If this becomes an issue, add `updatedAt` display so users can see staleness. |

## Delivery Log Edge Cases

| Scenario | Behavior |
|----------|----------|
| Error message is very long (multi-kilobyte webhook response) | Already handled — `.error-text` class truncates with ellipsis at 200px. Consider adding a tooltip on hover for the full message. |
| Delivery log has thousands of entries | Backend limits to 20 most recent. The UI shows "Showing latest 20" or similar if pagination is added later. For now, 20 is sufficient. |
| `sentAt` is null (delivery attempted but not sent) | Display "—" or "Pending" in the sent-at column. Handle null gracefully in the template: `{{ log.sentAt ? log.sentAt \| date : '—' }}` |

## Network / API Failures

| Scenario | Behavior |
|----------|----------|
| `/api/notifications/channels` fails on load | Channel schemas list stays empty. Create dialog can't open meaningfully. Show `MatSnackBar.error('Failed to load notification channels')`. Tab remains usable for viewing existing subscriptions if those loaded. |
| `/api/notifications/subscriptions/job/{jobId}` returns 500 | Subscription list stays empty. Error toast displayed (Task 5). Retry button in toast? Consider a "Retry" action on the snackBar: `this.snackBar.error('Failed to load', 'Retry').onAction()...` |
| CORS or network disconnect during form submit | HTTP error caught by component's error handler. Submit button re-enables. Form data preserved so user can retry. |

## Browser / Frontend Edge Cases

| Scenario | Behavior |
|----------|----------|
| User navigates away from job detail while dialog is open | MatDialog closes on navigation (Angular Router deactivates the parent component). No cleanup needed — the dialog ref loses its anchor and Angular destroys it. |
| User resizes browser window with delivery log expanded | MatTable handles responsive width. Horizontal scroll appears if columns exceed viewport. No special handling needed beyond ensuring `overflow-x: auto` on the table container. |
| Long channel type name in delivery log (custom channel) | Truncate with ellipsis and tooltip, same pattern as error text. Use a CSS class with `max-width`, `overflow: hidden`, `text-overflow: ellipsis`. |
