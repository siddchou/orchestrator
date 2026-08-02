# Phase 5 UI — Component Design

## 1. DynamicConfigFormComponent (NEW)

Extracted from `DynamicStepFormComponent`. This is the core shared form builder for any schema-driven configuration.

### Location
`orchestrator-ui/src/app/shared/components/dynamic-config-form/dynamic-config-form.ts`

### Interface

```typescript
interface ConfigSchema {
  fields: FieldDefinition[];
  title?: string;          // human-readable section label
}

@Component({
  selector: 'app-dynamic-config-form',
  standalone: true,
  imports: [DynamicFieldComponent, ...],
  templateUrl: './dynamic-config-form.html',
  styleUrl: './dynamic-config-form.scss'
})
export class DynamicConfigFormComponent {
  @Input() fields: FieldDefinition[] = [];
  @Input() title?: string;
  @Input() existingConfig?: Record<string, unknown>;
  @Input() credentials?: Credential[];   // for SECRET_REF fields

  @Output() formReady = new EventEmitter<void>();
  @Output() configValid = new EventEmitter<Record<string, unknown>>();

  readonly form: FormGroup;

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({});
  }

  // Inherited from DynamicStepFormComponent — unchanged logic:
  buildForm(): void;
  fieldValidators(field: FieldDefinition): Array<AbstractValidator<any> | NullValidator>;
  resolveInitialValue(field: FieldDefinition): unknown;
  toConfig(): Record<string, unknown>;
  validate(): boolean;
}
```

### Template
Renders `<app-dynamic-field>` for each field in `fields`, bound to corresponding form control. Wraps each in a `mat-form-field` with the field's `label` as floating label and `helpText` as hint.

### Migration impact
`DynamicStepFormComponent` becomes a thin wrapper:
```typescript
// DynamicStepFormComponent — after extraction
@Input() set schema(s: StepConfigSchema) { this._schema = s; this.delegate.buildForm(); }
// delegates all form logic to <app-dynamic-config-form [fields]="schema.fields" ... />
```

---

## 2. NotificationsTabComponent (EXISTING — BUG FIXES + ENHANCEMENTS)

### Location
`orchestrator-ui/src/app/features/jobs/notifications/notifications-tab.component.ts`

### Current issues to fix

**2.1 Empty state:** Add an empty state when subscription list is loaded and empty:
```html
<div class="empty-state" *ngIf="subscriptions().length === 0 && !loading()">
  <p>No notification subscriptions for this job.</p>
  <button mat-raised-button color="primary" (click)="openCreateDialog()">
    Create Subscription
  </button>
</div>
```

**2.2 Loading state:** Add a skeleton or spinner overlay on the table during initial load, not just on the "show delivery log" toggle.

**2.3 Error handling:** Wrap HTTP calls in `.pipe(catchError(...))` and display errors via `MatSnackBar`. Currently errors propagate silently.

### No structural changes
The component's data flow, dialog orchestration, and table rendering are sound. Only the three fixes above.

---

## 3. NotificationSubscriptionFormComponent (EXISTING — REFACTOR)

### Location
`orchestrator-ui/src/app/features/jobs/notifications/notification-subscription-form.component.ts`

### Current issues to fix

**3.1 Channel label bug:** In `open()`, the channel options map `label: schema.fields`. Fix to derive a proper display name:
```typescript
static typeToLabel(type: string): string {
  const map: Record<string, string> = {
    EMAIL: 'Email',
    SLACK_WEBHOOK: 'Slack Webhook',
    GENERIC_WEBHOOK: 'Generic Webhook'
  };
  return map[type] ?? type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}
```

**3.2 Refactor to use DynamicConfigFormComponent:** Replace the manual `buildConfigFields()` + manual FormGroup construction with a single `<app-dynamic-config-form>` embedding:

```html
<app-dynamic-config-form
  *ngIf="selectedSchema"
  [fields]="selectedSchema.fields"
  [existingConfig]="subscription?.config ?? {}"
  (formReady)="onConfigFormReady()"
  (configValid)="onConfigValid($event)">
</app-dynamic-config-form>
```

This eliminates ~40 lines of duplicated form-building logic and inherits validation from the shared component.

**3.3 Event selection:** The current checkbox-based event selection is fine. Keep as-is. Consider `mat-chip` selection for a more compact display if space is tight, but checkboxes are clearer for 5 options.

### Interface (unchanged)
```typescript
static open(
  dialog: MatDialog,
  channelSchemas: ChannelConfigSchema[],
  subscription?: NotificationSubscription
): MatDialogRef<NotificationSubscriptionFormComponent, NotificationSubscriptionRequest | null>
```

---

## 4. DeliveryLogComponent (EXISTING — ENHANCEMENT)

### Location
`orchestrator-ui/src/app/features/jobs/notifications/delivery-log.component.ts`

### Enhancement: Run-ID filter

Add an optional run ID input field above the table:

```html
<mat-form-field appearance="outline" class="run-filter">
  <mat-label>Filter by Run ID</mat-label>
  <input matInput type="number" [(ngModel)]="filterRunId" (keyup.enter)="load()" placeholder="e.g. 42">
  <button *ngIf="filterRunId" matSuffix mat-icon-button aria-label="Clear filter" (click)="filterRunId = null; load()">
    <mat-icon>close</mat-icon>
  </button>
</mat-form-field>
```

The backend already supports `?runId=` query parameter. Wire it through:
```typescript
load(): void {
  const params: Record<string, string> = { subscriptionId: this.subscriptionId };
  if (this.filterRunId) params.runId = this.filterRunId.toString();
  this.notificationService.getDeliveryLog(params).subscribe(...);
}
```

### No other structural changes
Status chips, column layout, and empty state are already implemented.

---

## 5. NotificationService (EXISTING — NO CHANGES)

The service layer is complete. All HTTP methods match the backend endpoints. One minor consideration: add `.pipe(catchError(...))` at the call site in components, not in the service, to keep the service as a thin HTTP wrapper. This follows the existing pattern in the codebase.

---

## 6. Notification Models (EXISTING — NO CHANGES)

The model definitions are correct and match the backend DTOs. One note: `events` on `NotificationSubscription` is a comma-separated string (backend storage format), while `NotificationSubscriptionRequest.events` is an array. The service layer handles this conversion implicitly via Jackson serialization on the backend; the UI sends arrays and receives strings, which the tab component splits for display.

---

## 7. Component Dependency Graph

```
JobDetailComponent
└── NotificationsTabComponent
    ├── NotificationSubscriptionFormComponent (dialog)
    │   └── DynamicConfigFormComponent (NEW — shared)
    │       └── DynamicFieldComponent (existing)
    └── DeliveryLogComponent
```

**No new services required.** The `NotificationService` covers all API needs.

**No routing changes.** The notifications feature is a tab within the existing job detail route.
