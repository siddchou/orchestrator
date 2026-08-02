# Phase 5 UI — Code Review Findings

## Date
2026-07-31

## Scope
Review of all existing notification-related UI code, dynamic form patterns, backend API shapes, and job detail page integration on branch `plan3-phase5`.

---

## 1. Existing Notification UI Components

All three notification UI components already exist on this branch and are wired into the job detail page.

### 1.1 NotificationsTabComponent
**Path:** `orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts`

- **Template:** Uses `mat-table` for subscription list, `mat-chips` for event display
- **Data flow:** Loads channel schemas on init (`listChannelSchemas()`), loads subscriptions via `getSubscriptionsForJob(jobId)`
- **Actions:** Create dialog, edit dialog, toggle active/inactive, delete with confirmation, delivery log toggle
- **Dialogs:** Opens `NotificationSubscriptionFormComponent` for create/edit. Uses `MatDialog` with a max-width of 600px
- **Delete confirmation:** Custom `confirmDeleteSubscription()` template dialog with subscription details display
- **Delivery log:** Conditionally renders `<app-delivery-log>` when `showingLogFor$` emits a subscription ID

### 1.2 NotificationSubscriptionFormComponent
**Path:** `orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts`

- **Mode:** Create and edit in one component, distinguished by presence of `subscriptionId`
- **Channel picker:** `mat-select` populated from channel schemas (mapped to `{ value: schema.type, label: schema.fields... }`) — actually uses `schema.fields` as label which is a bug; should use a display name derived from the type
- **Event selection:** Checkboxes for JOB_SUCCESS, JOB_FAILURE, JOB_STARTED, STEP_FAILURE, ON_SCHEDULE
- **Config fields:** Built manually via `buildConfigFields()` method that iterates over `selectedSchema.fields` and renders each with `<app-dynamic-field>` directly
- **No DynamicStepFormComponent reuse:** The form builds a plain `FormGroup` manually, calls `buildForm()`, and constructs the config object by iterating `this.configFields` and reading `this.form.get(field.name)!.value`
- **Validation:** Simple required-field check on config fields when `field.required === true`

### 1.3 DeliveryLogComponent
**Path:** `orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts`

- **Inputs:** `@Input() subscriptionId: string`
- **Display:** MatTable with columns: status (chip), channel type, run ID, attempt count, error message (truncated with ellipsis), sent-at date, created-at date
- **Status chips:** Color-coded — SUCCESS (green), FAILED (red), PENDING (orange)
- **Empty state:** "No delivery attempts recorded" when log is empty
- **Loading state:** `<mat-spinner>` while data loads

---

## 2. Dynamic Form Pattern (Phase 2)

### 2.1 DynamicStepFormComponent
**Path:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts`

- **Inputs:** `schema: StepConfigSchema`, `existingConfig?: Record<string, unknown>`, `credentials?: Credential[]`
- **Outputs:** `formReady: EventEmitter<void>`, `configValid: EventEmitter<Record<string, unknown>>`
- **Form building:** `buildForm()` iterates `schema.fields`, creates controls with `resolveInitialValue()`, applies validators from `fieldValidators()`
- **Validators:** Required check, SECRET_REF credential validation, minimum length for LIST_STRING
- **Config extraction:** `toConfig()` iterates fields and reads values, with LIST_STRING array-to-comma-string conversion
- **Handles:** STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING

### 2.2 DynamicFieldComponent
**Path:** `orchestrator-ui/src/app/shared/components/dynamic-field/dynamic-field.ts`

- **Inputs:** `field: FieldDefinition`, `formControl: AbstractControl`, `credentialOptions?: string[]`
- **Rendering:** Switch on `field.type`:
  - STRING → `mat-input` with optional `type="password"` for SECRET_REF
  - NUMBER → `mat-input` with `type="number"` and `step="any"`
  - BOOLEAN → `mat-checkbox`
  - ENUM → `mat-select` with `mat-option` for each enum value
  - SECRET_REF → `mat-select` populated from `credentialOptions`
  - FILE_PATTERN → `mat-input`
  - LIST_STRING → `mat-chip-grid` with inline input, comma-separated to array conversion

### 2.3 Key Difference: StepConfigSchema vs ChannelConfigSchema

```typescript
// Phase 2 (Steps)
interface StepConfigSchema {
  stepType: string;
  displayName: string;
  description?: string;
  fields: FieldDefinition[];
}

// Phase 5 (Notifications)
interface ChannelConfigSchema {
  type: string;
  fields: FieldDefinition[];
}
```

Both use the same `FieldDefinition[]` record (shared backend: `com.novakai.orchestrator.engine.spi.FieldDefinition`). The field types overlap — channels use STRING, LIST_STRING, ENUM; steps additionally use NUMBER, BOOLEAN, SECRET_REF, FILE_PATTERN.

---

## 3. Backend API

### 3.1 NotificationController
**Path:** `src/main/java/com/novakai/orchestrator/api/controller/NotificationController.java`

Base URL: `/api/notifications`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/channels` | Available channel types + field schemas |
| GET | `/subscriptions/job/{jobId}` | Subscriptions for a job |
| GET | `/subscriptions/{id}` | Single subscription |
| POST | `/subscriptions` | Create subscription |
| PUT | `/subscriptions/{id}` | Update subscription |
| DELETE | `/subscriptions/{id}` | Delete subscription |
| PATCH | `/subscriptions/{id}/toggle` | Toggle active/inactive |
| GET | `/delivery-log` | Delivery log (`subscriptionId`, `runId` query params) |

### 3.2 Channel Implementations

**EmailNotificationChannel:** `recipients` (LIST_STRING, required), `fromAddress` (STRING, optional). Conditional on JavaMailSender bean.

**SlackWebhookChannel:** `webhookUrl` (STRING, required), `channel` (STRING, optional)

**GenericWebhookChannel:** `webhookUrl` (STRING, required), `method` (ENUM: GET/POST/PUT/PATCH, default POST), `headers` (STRING, optional), `payload` (STRING, optional)

### 3.3 NotificationService.ts
**Path:** `orchestrator-ui/src/app/core/services/notification.service.ts`

All HTTP methods implemented. Uses `HttpClient` with proper generics. Base URL: `/api/notifications`.

---

## 4. Job Detail Page Integration

**Path:** `orchestrator-ui/src/app/features/jobs/job-detail/`

The job detail page already has 6 tabs: General, Steps, Environment, Schedule, Versions, Notifications. The Notifications tab is wired at lines 261-263 in the template:

```html
<mat-tab label="Notifications">
  <app-notifications-tab [jobId]="jobId!"></app-notifications-tab>
</mat-tab>
```

Component imports `NotificationsTabComponent` in standalone declarations. Integration is complete.

---

## 5. Models and Service Layer

### 5.1 notification.model.ts
Defines all TypeScript interfaces for the notification feature:
- `ChannelConfigSchema { type: string; fields: FieldDefinition[] }`
- `NotificationSubscription { id, jobId, channelType, events (comma-separated), active, createdAt }`
- `NotificationSubscriptionRequest { jobId, channelType, events[], config }`
- `NotificationDeliveryLog { id, subscriptionId, runId, channelType, status, attemptCount, errorMessage, createdAt, sentAt }`

### 5.2 notification.service.ts
HTTP service with methods matching all backend endpoints. Properly typed with RxJS Observables.

---

## 6. Issues Found

### 6.1 Channel label bug in form component
In `NotificationSubscriptionFormComponent.open()`, channel options are mapped as:
```typescript
label: schema.fields  // BUG: should be a human-readable name
```
This displays `[object Object]` or an array reference instead of the channel type name. Should derive label from `schema.type` (e.g., "EMAIL" → "Email", "SLACK_WEBHOOK" → "Slack Webhook").

### 6.2 No reuse of DynamicStepFormComponent's form-building logic
The notification subscription form manually builds config fields and validates them, duplicating logic that exists in DynamicStepFormComponent (`buildForm()`, `fieldValidators()`, `resolveInitialValue()`, `toConfig()`). This is code duplication — ~40 lines of form logic could be shared.

### 6.3 Missing validation feedback
The notification subscription form's `validateConfigFields()` method sets `invalidConfigFields` Set but the template validation messaging is minimal — it uses `mat-error` with "This field is required" but doesn't leverage DynamicStepFormComponent's richer validation (enum constraints, SECRET_REF credential validation).

### 6.4 Delivery log component lacks subscription-level filtering
The delivery log loads all deliveries for a subscription on init but the backend supports `runId` filtering. There's no UI to filter by run ID, which could be useful for debugging specific runs.

### 6.5 No empty state for subscription table
The notifications tab doesn't have an empty state when no subscriptions exist. Users see an empty table with headers but no guidance on what to do.

---

## 7. Files Reviewed

| File | Purpose |
|------|---------|
| `notifications-tab.component.ts` | Main notification tab controller |
| `notifications-tab.component.html` | Subscription list template |
| `notifications-tab.component.scss` | Tab styling |
| `notification-subscription-form.component.ts` | Create/edit subscription dialog |
| `notification-subscription-form.component.html` | Form template with channel picker, events, config fields |
| `notification-subscription-form.component.scss` | Form styling |
| `delivery-log.component.ts` | Delivery log table controller |
| `delivery-log.component.html` | Log table template |
| `delivery-log.component.scss` | Log table styling |
| `dynamic-step-form.ts` | Phase 2 dynamic form builder |
| `dynamic-field.ts` | Phase 2 dynamic field renderer |
| `notification.model.ts` | TypeScript interfaces |
| `notification.service.ts` | HTTP service layer |
| `job-detail.component.ts` | Job detail page with tabs |
| `job-detail.component.html` | Tab group template |
| `NotificationController.java` | Backend REST controller |
| `EmailNotificationChannel.java` | Email channel SPI impl |
| `SlackWebhookChannel.java` | Slack webhook channel SPI impl |
| `GenericWebhookChannel.java` | Generic webhook channel SPI impl |
| `FieldDefinition.java` | Shared field definition record |

**Total:** 19 files reviewed.
