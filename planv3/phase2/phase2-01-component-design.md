# Phase 2 — Component Design (As-Built)

All components described below **exist in the codebase**. This document captures their design for reference, not as a specification for new work.

---

## 1. DynamicFieldComponent

**File:** `orchestrator-ui/src/app/shared/components/dynamic-field/`

### Purpose
Renders a single form field based on a `FieldDefinition` schema entry. Switches on `FieldType` to produce the appropriate Material control.

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| `fieldDef` | `FieldDefinition` | Input | Schema definition (name, label, type, required, defaultValue, enumValues, helpText) |
| `control` | `FormControl` | Input | Reactive form control to bind to |

### Rendered Variants

| FieldType | Material Control | Details |
|-----------|-----------------|---------|
| `STRING` | `<input matInput>` | Text input with label and hint |
| `FILE_PATTERN` | `<input matInput>` | Text input with pattern hint text |
| `LIST_STRING` | Chip list + input | Enter-to-add, Backspace-to-remove last chip |
| `NUMBER` | `<input matInput type="number">` | Numeric input with min/max from schema |
| `BOOLEAN` | `<mat-checkbox>` | Checkbox toggle |
| `ENUM` | `<mat-select>` | Dropdown populated from `fieldDef.enumValues` |
| `SECRET_REF` | `<mat-select>` | Credential dropdown populated from credentials list |
| *(unknown)* | `<input matInput>` with warning | Fallback with error message about unsupported type |

### State Management
None — purely presentational. All state flows through the `FormControl`.

### Key Implementation Details
- Uses `*ngSwitch` on `fieldDef.type` for variant rendering
- `KNOWN_TYPES` set validates that the field type is recognized
- Chip input helpers: `addChip(event)`, `removeChip(index)`
- Error messaging via `control.errors` display

---

## 2. DynamicStepFormComponent

**File:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/`

### Purpose
Builds a complete reactive form from a `StepConfigSchema`. Orchestrates multiple `DynamicFieldComponent` instances, one per field in the schema.

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| `schema` | `StepConfigSchema` | Input | Schema with stepType, displayName, fields list |
| `existingConfig` | `Record<string, unknown> \| null` | Input | Existing config values to pre-populate |
| `credentials` | `Credential[]` | Input | Available credentials for SECRET_REF fields |

### Public Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `validate()` | `void` | Marks all controls as dirty/touched to trigger validation display |
| `toConfig()` | `Record<string, unknown>` | Converts form values back to config JSON (handles LIST_STRING → array) |

### State Management
- Maintains an internal `FormGroup` built from schema fields
- `ngOnChanges` rebuilds the FormGroup when `schema`, `existingConfig`, or `credentials` change
- Resolves initial field values: `existingConfig[field.name] ?? fieldDef.defaultValue`

### Key Implementation Details
- Iterates over `schema.fields` to create validators (`required` → `Validators.required`)
- Uses `<ng-container *ngFor>` with `DynamicFieldComponent` for each field
- LIST_STRING conversion: form stores as string array, `toConfig()` preserves array format
- Credential validation (E6): validates that SECRET_REF values reference existing credential IDs

---

## 3. StepPaletteComponent

**File:** `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.ts`

### Purpose
Dialog that lists available step types for selection. Called when adding a new step to a job.

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| *(dialog data)* | `MatDialogData<unknown>` | Input | No specific data needed — fetches from API |

### Data Flow
1. Component loads step types via `JobService.listStepTypes()` → `GET /api/step-types`
2. Displays each step type with icon (from hardcoded map), displayName, and description
3. Text filter input narrows list client-side
4. User clicks a step type → dialog closes with `stepType` string

### State Management
- Local state for filtered step type list
- Hardcoded metadata map: `{ stepType: { icon: '...', description: '...' } }`

### Gap Identified
Hardcoded metadata means **new step types appear without an icon or description**. Should fall back to `displayName` from schema and a default icon (e.g., `settings`).

---

## 4. StepFormDialog

**File:** `orchestrator-ui/src/app/features/jobs/step-builder/step-form-dialog.ts`

### Purpose
Wraps `DynamicStepFormComponent` in a dialog with step metadata fields (name, type selector, continueOnFailure, enabled).

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| `dialogData` | `{ stepType: string, edit?: boolean, existingConfig?: Record<string, unknown> }` | Input | Step type to configure, optional existing config for edits |

### Data Flow
1. Receives `stepType` from palette selection (or existing step on edit)
2. Fetches schema for that step type via API
3. Loads credentials for SECRET_REF fields
4. Renders `DynamicStepFormComponent` with schema + existingConfig
5. On submit: extracts config via `@ViewChild(DynamicStepFormComponent).toConfig()`

### State Management
- Local form for step name, continueOnFailure, enabled
- Schema and credentials loaded from API on init
- `isStepTypeRemoved` getter checks if selected step type still exists in registry (E5 edge case)

---

## 5. TeamSwitcherComponent

**File:** `orchestrator-ui/src/app/shared/components/team-switcher/`

### Purpose
Dropdown for switching active team. Integrated in the app shell toolbar.

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| *(none)* | — | — | Loads teams from API on init |

### Data Flow
1. On init: calls `TeamService.listMyTeams()` → `GET /api/teams/my-teams`
2. Displays dropdown with team names and current active team highlighted
3. On selection: checks FormGuard for unsaved changes (E2)
4. Calls `TeamService.setActiveTeam(teamId)` → `POST /api/teams/active/{id}`
5. Triggers page reload to refresh all data with new team context

### State Management
- Local state for team list and active team ID
- **Retry logic** (E12): retries API call on failure before showing error
- **sessionStorage cache fallback**: if API fails, uses cached teams from sessionStorage

---

## 6. RunTimelineComponent

**File:** `orchestrator-ui/src/app/shared/components/run-timeline/`

### Purpose
Horizontal bar chart visualization of job run step timing. Shows start/end times and status colors for each step in a run.

### Inputs / Outputs

| Binding | Type | Direction | Description |
|---------|------|-----------|-------------|
| `run` | `JobRun` | Input | Run object with steps array (each having startTime, endTime, status) |

### Computed State
- **Bar positions:** computed from step `startTime` and `endTime` relative to run start time
- **Time axis ticks:** generated at regular intervals across the run duration
- **Status colors:** mapped from step status (`SUCCESS` → green, `FAILED` → red, etc.)

### Key Implementation Details
- Uses SVG or div-based rendering for bars (check template)
- Handles edge cases: steps with no end time (running), zero-duration steps
- Generates readable time labels for axis ticks

---

## 7. ThemeService

**File:** `orchestrator-ui/src/app/core/services/theme.service.ts`

### Purpose
Manages light/dark theme state using Angular signals. Provides toggle functionality and persists preference.

### API

| Method | Returns | Description |
|--------|---------|-------------|
| `theme()` | `Signal<'light' \| 'dark'>` | Computed signal for current theme |
| `toggle()` | `void` | Switches between light and dark |

### State Management
- **Signal-based** state (not BehaviorSubject) — leverages Angular's reactivity system
- **`effect()`** side effect: syncs `data-theme` attribute on `<html>` element and localStorage
- **Init logic:** checks localStorage first, then `prefers-color-scheme` media query, defaults to `'light'`

---

## 8. FormGuardService

**File:** `orchestrator-ui/src/app/core/services/form-guard.service.ts`

### Purpose
Detects unsaved changes across components. Prevents navigation away from dirty forms without confirmation.

### API

| Method | Returns | Description |
|--------|---------|-------------|
| `markDirty()` | `void` | Marks current form as having unsaved changes |
| `markClean()` | `void` | Marks current form as saved |
| `check()` | `boolean \| string` | Returns false if clean, or confirmation message if dirty (for Angular guards) |

### Integration Points
- **TeamSwitcherComponent:** calls `check()` before switching teams
- **Route guard:** can be used as a route guard in `app.routes.ts`

---

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  App Shell (app.ts)                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ ThemeService  │  │TeamSwitcher  │  │    RouterOutlet  │   │
│  │ (signal-based)│  │(form guard)  │  │                  │   │
│  └──────┬───────┘  └──────┬───────┘  │                  │   │
│         │                 │          ├─ JobDetail        │   │
│         │                 │          │   ┌────────────┐  │   │
│         │                 │          │   │StepPalette │  │   │
│         │                 │          │   └──────┬─────┘  │   │
│         │                 │          │          │        │   │
│         │                 │          │   ┌──────▼─────┐  │   │
│         │                 │          │   │StepFormDlg │  │   │
│         │                 │          │   │ ┌────────┐ │  │   │
│         │                 │          │   │ │DynStep │ │  │   │
│         │                 │          │   │ │Form    │ │  │   │
│         │                 │          │   │ │ ┌────┐ │ │  │   │
│         │                 │          │   │ │ │DynF │ │ │  │   │
│         │                 │          │   │ │ └────┘ │ │  │   │
│         │                 │          │   │ └────────┘ │  │   │
│         │                 │          │   └────────────┘  │   │
│         │                 │          ├─ RunDetail        │   │
│         │                 │          │   ┌────────────┐  │   │
│         │                 │          │   │RunTimeline │  │   │
│         │                 │          │   └────────────┘  │   │
│         │                 │          └──────────────────┘   │
│         │                 │                                 │
└─────────┼─────────────────┼─────────────────────────────────┘
          │                 │
    [data-theme]      Team API (JWT-scoped)
    localStorage
```
