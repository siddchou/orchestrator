# Phase 3 UI — Code Review Findings

## 1. Backend Dependency Model API Shape — CONFIRMED

**Entity:** `JobStepDependency` at `src/main/java/.../domain/entity/JobStepDependency.java`
- Fields: `dependencyId`, `step` (FK), `dependsOnStep` (FK), `edgeCondition` (enum)
- Enum values: `ON_SUCCESS`, `ON_FAILURE`, `ALWAYS`

**DTOs:**
- `StepDependencyRequest`: `{ dependsOnStepId: Long, edgeCondition: String }` — defaults to `"ON_SUCCESS"` if blank
- `StepDependencyResponse`: `{ stepDependencyId, dependsOnStepId, dependsOnStepName, edgeCondition }`

**API Endpoints (in `JobDefinitionController.java`):**
| Method | Path | Body/Returns |
|--------|------|-------------|
| GET | `/api/jobs/{id}/steps/{stepId}/dependencies` | `List<StepDependencyResponse>` |
| PUT | `/api/jobs/{id}/steps/{stepId}/dependencies` | `List<StepDependencyRequest>` → void |

**DagExecutionEngine** uses `JobStepDependencyRepository`, has `.buildDag()`, `.loadDependencies()`, `.canStepProceed()`, `.signalDependents()` methods — DAG execution is fully implemented.

## 2. Phase 2 Component APIs to Reuse — CONFIRMED

### DynamicStepFormComponent
- **Path:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts`
- **Selector:** `<app-dynamic-step-form>`
- **Inputs:**
  - `@Input() schema: StepConfigSchema` — field definitions from step-type registry
  - `@Input() existingConfig: Record<string, unknown> | null` — pre-populated values
  - `@Input() credentials: Credential[]` — for SECRET_REF validation
- **Public methods:** `validate(): boolean`, `toConfig(): { config, valid }`
- **Standalone:** true

### StepPaletteComponent
- **Path:** `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.ts`
- **Selector:** `<app-step-palette>`
- **Usage:** Opened as `MatDialog`, returns `{ stepType: string }` on close
- **Helper function exported:** `iconFor(stepType)` — maps step types to Material icons

### StepFormDialog (wraps DynamicStepFormComponent)
- **Path:** `orchestrator-ui/src/app/features/jobs/step-builder/step-form-dialog.ts`
- **Selector:** `<app-step-form-dialog>`
- **Data interface:** `StepFormData { stepId?, stepName, stepOrder, stepType, stepConfig, continueOnFailure, enabled }`

## 3. Current Job Editor Structure — CONFIRMED

**JobDetailComponent** at `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.ts`:
- Tab-based layout: General | Steps | Environment | Schedule
- **Steps tab:** Linear list using Angular CDK drag-drop (`cdkDropList`, `cdkDrag`) for reordering
- Step actions: edit (opens `StepFormDialog`), delete
- Uses `CdkDrag, CdkDropList, CdkDragHandle` from `@angular/cdk/drag-drop`

**DAG canvas stub exists:**
- **Path:** `orchestrator-ui/src/app/features/jobs/dag-canvas-stub/` (`.ts`, `.html`, `.scss`)
- **Route:** `/jobs/:id/canvas` in `app.routes.ts` — loads `DagCanvasStubComponent`
- Currently shows a "Coming Soon" card

## 4. Angular CDK Version & Drag-Drop Usage — CONFIRMED

- **Angular version:** 21.2.x (`@angular/core`: 21.2.18)
- **CDK version:** `@angular/cdk`: 21.2.14
- **`@angular/cdk/drag-drop`** is already used in `JobDetailComponent` for step reordering

## 5. Graph-Rendering Library — NOT PRESENT

No graph/DAG visualization library found in `package.json`. Dependencies are:
- Angular framework (core, common, forms, router, animations)
- Angular Material + CDK
- rxjs, zone.js, tslib
- **No** ngx-graph, d3, cytoscape, dagre, or elkjs

## 6. Status Color Conventions — CONFIRMED

### RunTimelineComponent (`run-timeline.ts`)
```typescript
const STATUS_COLOR_VARS: Record<RunStatus, string> = {
  PENDING:   'var(--status-pending-bg, #78909c)',
  RUNNING:   'var(--status-running-bg, #ff9800)',
  SUCCESS:   'var(--status-success-bg, #4caf50)',
  FAILED:    'var(--status-failed-bg, #f44336)',
  PARTIAL:   'var(--status-partial-bg, #ff9800)',
  CANCELLED: 'var(--status-cancelled-bg, #9c27b0)',
};
```

### StatusBadge (`status-badge.ts`)
- Uses CSS custom properties: `--status-{pending,running,success,failed,partial,cancelled}-bg`
- Takes `@Input() status: RunStatus`, exposes `color` and `statusLabel` getters

## 7. Live Updates — POLLING ONLY

- **SSE endpoint:** `/api/runs/{runId}/log-stream` streams log lines only (no step-status events)
- **Step status updates:** Polling every 3 seconds via `GET /api/runs/{runId}` in `RunDetailComponent`
- No dedicated step-status SSE stream exists

## 8. Frontend Model Gap — TO ADD

Current `JobStep` interface in `job.model.ts` lacks dependency data:
```typescript
export interface JobStep {
  stepId: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;
  continueOnFailure: boolean;
  enabled: boolean;
  // MISSING: dependencies array
}
```

The backend GET job endpoint likely returns steps without embedded dependency edges (separate API call needed per step). The frontend model must be extended with a `dependencies` field.

## [NOT FOUND IN REPO] Items

1. **Dedicated step-status SSE stream** — only log-stream SSE exists; run-detail polls for status
2. **Graph layout library** — no DAG auto-layout library in dependencies
3. **Backend job DTO with embedded edges** — dependency data requires separate API call per step via `GET /{id}/steps/{stepId}/dependencies`
