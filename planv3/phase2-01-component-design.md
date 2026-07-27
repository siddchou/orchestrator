# Phase 2 — Component Design

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  App Shell (app.ts)                                      │
│  ┌──────────────┬──────────────────────────────────────┐ │
│  │ TeamSwitcher  │  Router Outlet                       │ │
│  │ Component    │  (jobs, runs, dashboard, config…)     │ │
│  └──────────────┴──────────────────────────────────────┘ │
│                     ▲                                    │
│                     │ uses                               │
│  ┌──────────────────┴──────────────────────────────────┐ │
│  │  AuthService (BehaviorSubject<AuthUser>)             │ │
│  │  → extended with teams: Team[], activeTeamId: number │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘

Feature components that change:
  JobDetailComponent    → uses DynamicStepFormComponent (already does)
  StepPaletteComponent  → made API-driven, removes hardcoded metadata
  RunDetailComponent    → adds RunTimelineComponent alongside step table

Shared components that change:
  DynamicFieldComponent     → hardening: validation feedback, credential picker
  DynamicStepFormComponent  → hardening: per-field error display, type safety
```

---

## 1. DynamicFieldComponent (hardened)

**File:** `orchestrator-ui/src/app/shared/components/dynamic-field/dynamic-field.ts`

### Current State
Already renders all 7 `FieldType` variants with a `*ngSwitch`. Has chip input for LIST_STRING, fallback for unknown types.

### Changes

| Change | Detail |
|--------|--------|
| **Validation feedback** | Add `@Input() showError: boolean = false` — when true and control has errors, render `<mat-error>` below the field with the first error message. This lets parent components trigger validation display on submit/blur. |
| **SECRET_REF → credential picker** | Replace free-text input with a `<mat-select>` populated from an injected `CredentialService.listCredentials()`. Shows only non-SSH_KEY credentials by default, or all if schema helpText contains "ssh". Falls back to text input if API call fails (graceful degradation). |
| **Unsupported type fallback** | The existing `*ngSwitchDefault` renders a text input. Change this to render the text input **plus** a warning banner: `"⚠ Field type '{type}' is not supported by this UI — value will be stored as text."` This surfaces the problem to users instead of silently accepting wrong data. |
| **Required indicator styling** | Move `*` from label text to a CSS pseudo-element or `<span class="required">` for consistent color (accent-danger red). Currently it's plain text appended to the label. |

### Inputs / Outputs

```typescript
@Input() fieldDef!: FieldDefinition;       // schema field definition
@Input() control!: FormControl;            // reactive form control
@Input() showError: boolean = false;       // trigger validation display
@Input() credentials?: Credential[];       // for SECRET_REF type (optional, lazy-loaded)
```

No outputs — state flows through the shared `FormControl`.

### FieldType → Material Control Mapping

| FieldType | Material Component | Validation Display |
|-----------|-------------------|-------------------|
| STRING | `<input matInput>` in `<mat-form-field>` | `<mat-error>` for required |
| NUMBER | `<input matInput type="number">` | `<mat-error>` for required |
| BOOLEAN | `<mat-checkbox>` | Visual: checkbox with red label if required+untouched on submit |
| ENUM | `<mat-select>` with `<mat-option>` per enumValues | `<mat-error>` for required |
| SECRET_REF | `<mat-select>` populated from `CredentialService` (fallback: text input) | `<mat-error>` for required |
| FILE_PATTERN | `<input matInput>` + `<mat-hint>` with glob syntax | `<mat-error>` for required |
| LIST_STRING | Chip container with `<mat-chip>` + hidden input | Warning text if required but empty |
| **unknown** | Text input + warning banner | Same as STRING |

---

## 2. DynamicStepFormComponent (hardened)

**File:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts`

### Current State
Builds a `FormGroup` from schema, resolves initial values, converts LIST_STRING to array on export. No per-field error display.

### Changes

| Change | Detail |
|--------|--------|
| **Per-field error state** | Add `touchedFields: Set<string>` — when `validate()` is called, mark all controls as touched and add their names to the set. Each `app-dynamic-field` gets `[showError]="touchedFields.has(field.name) && control.invalid"`. |
| **Credential data passthrough** | Accept optional `@Input() credentials: Credential[]` and pass it down to each `DynamicFieldComponent`. This avoids every field component making its own API call. |
| **Schema change detection** | If `schema` input reference changes (e.g., user switches step type in the parent dialog), rebuild the form. Currently `ngOnInit` builds once; add `ngOnChanges` to handle schema swaps. |
| **Export validation summary** | `toConfig()` should return `{ config: Record<string, unknown>, valid: boolean }` tuple so callers can gate submission on validity. |

### Inputs / Outputs

```typescript
@Input() schema!: StepConfigSchema;
@Input() existingConfig: Record<string, unknown> | null = null;
@Input() credentials?: Credential[];        // new: for SECRET_REF fields

validate(): boolean;                        // marks all touched, returns true if valid
toConfig(): { config: Record<string, unknown>; valid: boolean };  // hardened return type
```

### Template Change

The template (`dynamic-step-form.html`) currently iterates `schema.fields` and renders `<app-dynamic-field>` per field. Add error display wiring:

```html
@for (field of fields; track field.name) {
  <app-dynamic-field
    [fieldDef]="field"
    [control]="form.get(field.name)"
    [showError]="touchedFields.has(field.name) && form.get(field.name)?.invalid"
    [credentials]="credentials">
  </app-dynamic-field>
}
```

---

## 3. StepPaletteComponent (API-driven)

**File:** `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.ts`

### Current State
Hardcoded `STEP_TYPE_META` map with icon + description per step type. Falls back to generic icon/description for unknown types. Fetches schemas from API but only uses them for the list — metadata is local.

### Changes

| Change | Detail |
|--------|--------|
| **Remove hardcoded metadata** | Delete `STEP_TYPE_META`. Use `schema.displayName` directly from the API response. For icons, use a default Material icon (`play_arrow`) or derive from step type name conventions (e.g., if stepType contains "HTTP" → `cloud_upload`, "DB" → `database`). This is a best-effort heuristic, not a requirement. |
| **Add schema field count** | Show the number of config fields per step type (`schema.fields.length`) as a badge — helps users gauge complexity at a glance. |
| **Empty state** | If API returns an empty array or fails, show a message: "No step types available. Check that Phase 1 executors are registered." with a retry button. |

### Inputs / Outputs

```typescript
// Dialog-ref based — no @Inputs
// dialogRef.close({ stepType: string, displayName: string })  // enriched return value
```

The component opens via `MatDialog`, fetches schemas on init, and closes with the selected schema's type + display name.

---

## 4. TeamSwitcherComponent (new)

**File:** `orchestrator-ui/src/app/shared/components/team-switcher/team-switcher.ts`

### Purpose
Dropdown in the app shell toolbar that lets users switch their active team. Only ADMIN and OPERATOR roles can switch; VIEWER sees their assigned team read-only.

### Design

```typescript
@Component({
  selector: 'app-team-switcher',
  standalone: true,
  imports: [CommonModule, MatSelectModule, MatFormFieldModule, MatIconModule],
})
export class TeamSwitcherComponent implements OnInit {
  teams: Team[] = [];           // teams this user belongs to
  activeTeamId: number | null;  // currently selected team
  loading = true;

  constructor(
    private teamService: TeamService,   // new service
    private authService: AuthService     // extended with team context
  ) {}

  ngOnInit(): void {
    this.teamService.listMyTeams().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.teams = res.data;
          this.activeTeamId = this.authService.getActiveTeamId();
        }
        this.loading = false;
      },
    });
  }

  switchTo(teamId: number): void {
    this.teamService.setActiveTeam(teamId).subscribe({
      next: () => {
        this.activeTeamId = teamId;
        this.authService.setActiveTeamId(teamId);
        // Reload current page to reflect new team scope
        window.location.reload(); // simple approach; could be router.navigate instead
      },
    });
  }
}
```

### Placement
Inserted into the app shell toolbar, next to the user avatar/username. Uses a compact `<mat-select>` with no form-field wrapper (just icon + select).

### State Management
- `AuthService` extended with `activeTeamId: number | null`, persisted in sessionStorage alongside auth token
- Backend stores active team in HTTP session (Spring `HttpSession.setAttribute('teamId', ...)`) or as a JWT claim refreshed on `/api/teams/active` POST
- All job/run API calls are scoped server-side using this session value — **no client-supplied team ID in request params**

### New Backend Endpoint

```
GET  /api/teams/my-teams        → List<TeamSummary>   // teams current user belongs to
POST /api/teams/active/{teamId} → void                // set active team for this session
GET  /api/teams/active          → TeamSummary         // get current active team
```

---

## 5. RunTimelineComponent (new)

**File:** `orchestrator-ui/src/app/shared/components/run-timeline/run-timeline.ts`

### Purpose
Horizontal Gantt-like visualization of a job run's steps, showing start/end times and duration as colored bars. Complements the existing step table in `RunDetailComponent`.

### Design

```typescript
@Component({
  selector: 'app-run-timeline',
  standalone: true,
  imports: [CommonModule],
})
export class RunTimelineComponent {
  @Input() run!: JobRunDetail;   // the full run with steps[]
  @Input() height: number = 200; // px height of the timeline area

  // Computed: total duration in ms from first step start to last step end
  get totalDurationMs(): number { ... }

  // Computed: map each step to position/width percentages
  get stepBars(): StepBar[] { ... }

  statusColor(status: RunStatus): string {
    return { SUCCESS: '#16a34a', FAILED: '#dc2626', RUNNING: '#ea580c',
             PENDING: '#9ca3af', PARTIAL: '#a855f7', CANCELLED: '#6b7280' }[status] ?? '#9ca3af';
  }
}

interface StepBar {
  stepName: string;
  stepOrder: number;
  leftPct: number;    // percentage of total timeline width
  widthPct: number;   // percentage of total timeline width
  color: string;      // status-based color
  durationLabel: string; // formatted duration (uses DurationPipe)
}
```

### Template Approach
Pure CSS — no canvas, no SVG library. Each step is a `<div>` positioned absolutely within a relative container:

```html
<div class="timeline-container" [style.height.px]="height">
  <!-- time axis -->
  <div class="timeline-axis">
    @for (tick of timeTicks; track tick.value) {
      <span class="tick" [style.left.%]="tick.pct">{{ tick.label }}</span>
    }
  </div>
  <!-- step bars -->
  @for (bar of stepBars; track bar.stepOrder) {
    <div class="step-bar"
         [style.left.%]="bar.leftPct"
         [style.width.%]="bar.widthPct"
         [style.background-color]="bar.color"
         [title]="'{{bar.stepName}}: {{bar.durationLabel}}'">
      <span class="step-label">{{ bar.stepName }}</span>
    </div>
  }
</div>
```

### Integration
Added to `RunDetailComponent` as a tab or section above the existing step table. The run detail already has `MatTabsModule` — add a "Timeline" tab alongside "Steps" and "Logs".

---

## 6. DagCanvasStubComponent (new)

**File:** `orchestrator-ui/src/app/features/jobs/dag-canvas-stub/dag-canvas-stub.ts`

### Purpose
Placeholder component at `/jobs/:id/canvas` that signals the DAG view is coming in Phase 3.

```typescript
@Component({
  selector: 'app-dag-canvas-stub',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="empty-state">
      <mat-icon style="font-size:64px;width:64px;height:64px;">graphical</mat-icon>
      <h3>DAG Canvas — Coming Soon</h3>
      <p>This view will show your job steps as a visual dependency graph.</p>
      <p>Scheduled for Phase 3, after the dependency model is implemented.</p>
      <!-- TODO(phase3): Replace this stub with the DAG canvas component. -->
      <!-- See planv3/phase3-01-dependency-model.md for the dependsOn schema and edge-condition model. -->
    </div>
  `
})
export class DagCanvasStubComponent {}
```

Route added to `app.routes.ts`:
```typescript
{ path: 'jobs/:id/canvas', loadComponent: () => import('@features/jobs/dag-canvas-stub/...') },
```

---

## State Management Summary

| Concern | Mechanism | Where |
|---------|-----------|-------|
| Auth token + role | `BehaviorSubject<AuthUser>` in sessionStorage | `AuthService` (existing) |
| Active team ID | Extended `AuthService` with `activeTeamId`, sessionStorage | `AuthService` (extended) |
| Team list for user | Fresh API call on team switcher init | `TeamService.listMyTeams()` |
| Step type schemas | Cached in component, fetched from `/api/step-types` | `StepPaletteComponent`, `StepFormDialog` |
| Credentials for SECRET_REF | Fetched by parent (`StepFormDialog`) and passed down as `@Input()` | `CredentialService` (existing) |
| Form state per step dialog | Reactive `FormGroup` scoped to dialog instance | `DynamicStepFormComponent` |
| Dark mode preference | `localStorage.getItem('theme')`, CSS class on `<html>` | New `ThemeService` or direct DOM manipulation in a small utility |

**No NgRx, no global store.** The app is small enough that `BehaviorSubject` services + `@Input()`/`@Output()` suffice. Adding a state library would be over-engineering at this scale.
