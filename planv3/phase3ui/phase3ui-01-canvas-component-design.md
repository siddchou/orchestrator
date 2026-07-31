# Phase 3 UI — Canvas Component Design

## Architecture Overview

The DAG canvas is composed of three main components plus two services, all under `orchestrator-ui/src/app/features/jobs/dag-canvas/`:

```
dag-canvas/
├── dag-canvas.component.ts        # Main canvas (pan/zoom, SVG layer, node orchestration)
├── dag-node.component.ts          # Individual step node (icon, label, status ring, drag handle)
├── dag-edge-renderer.ts           # Pure function: computes SVG path data from node positions
├── edge-condition-picker.component.ts  # Dropdown for selecting ON_SUCCESS/ON_FAILURE/ALWAYS
├── services/
│   ├── dag-layout.service.ts      # Auto-layout algorithm (layered, top-to-bottom)
│   └── cycle-detector.service.ts  # DFS-based cycle detection
└── run-dag-canvas.component.ts    # Read-only variant for run detail view
```

## Data Model Extensions

### New: `JobStepWithDependencies` interface
```typescript
export interface StepDependency {
  stepDependencyId?: number;
  dependsOnStepId: number;
  dependsOnStepName: string;
  edgeCondition: EdgeCondition;
}

export type EdgeCondition = 'ON_SUCCESS' | 'ON_FAILURE' | 'ALWAYS';

export interface JobStepWithDependencies extends JobStep {
  dependencies: StepDependency[];
}
```

### New: Canvas-internal node model
```typescript
interface DagNodeModel {
  stepId: number | null;       // null = temporary, unsaved node
  stepName: string;
  stepType: string;
  position: { x: number; y: number };
  size: { width: number; height: number };   // computed from template ref
  dependencies: StepDependency[];
  status?: RunStatus;           // only in run-view mode
}
```

## Component 1: DagCanvasComponent (edit mode)

**Selector:** `<app-dag-canvas>`
**Mode:** Edit — full interactivity

### Inputs / Outputs
```typescript
@Input() jobId!: number;
@Input() steps!: JobStepWithDependencies[];   // populated by parent after fetching deps
@Output() dependenciesChanged = new EventEmitter<{ stepId: number; deps: StepDependencyRequest[] }>();
@Output() stepSelected = new EventEmitter<JobStep>();    // opens StepFormDialog
@Output() stepDeleted = new EventEmitter<number>();      // stepId to delete
@Output() addStepRequested = new EventEmitter<void>();   // opens StepPaletteComponent
```

### Visual Structure
- **Container:** Full-width div with overflow hidden, relative positioning
- **Canvas surface:** Absolutely positioned div, transformed via CSS `transform: translate() scale()` for pan/zoom
- **SVG layer:** Full-size `<svg>` overlay for edges (behind nodes)
  - Each edge is a `<path>` element with cubic bezier curves
  - Edge label rendered as `<text>` at midpoint
- **Node layer:** Absolutely positioned `<app-dag-node>` elements on top of SVG

### Pan & Zoom
- Mouse wheel → zoom (0.3x to 2x), centered on cursor position
- Middle-mouse drag or shift+drag on empty canvas → pan
- Zoom controls: `+` / `-` / `fit` buttons in bottom-right corner
- State stored as `{ offsetX, offsetY, scale }`, applied via CSS transform

### Node Rendering (delegated to DagNodeComponent)
Each node is a card (~160px × 72px):
- **Left:** Step-type icon from `iconFor()` helper (reused from step-palette)
- **Center:** Step name (truncated with ellipsis at 16 chars), step type label below
- **Right:** Port handles — small circles for edge creation
  - Top-right port: "output" — drag FROM here to create dependency
  - Bottom-left port: "input" — drop TO here to add dependency
- **Hover state:** Shows delete button (×) in top-right corner

### Edge Rendering
- Edges are SVG `<path>` elements using cubic bezier curves
- Source: output port of upstream step → Target: input port of downstream step
- Path computation: `dag-edge-renderer.ts` pure function, takes two node positions + sizes
- Arrowhead at target end via SVG `<marker>`
- **Condition label:** Small pill-shaped badge at edge midpoint showing condition abbreviation (✓ for ON_SUCCESS, ✗ for ON_FAILURE, ∞ for ALWAYS)
- Edge color: `--primary-color` in edit mode

### Add-Edge Interaction
1. User clicks and drags from a node's output port
2. A temporary rubber-band line follows the cursor (SVG `<line>`)
3. Valid target nodes highlight their input ports on hover
4. On drop onto a valid target's input port:
   - **Cycle check:** `cycleDetector.wouldCreateCycle(sourceStepId, targetStepId)` — if true, show snackbar "Cannot create circular dependency" and abort
   - If valid, open the edge-condition picker (inline dropdown at drop point)
5. User selects condition → edge is added to local state
6. On save/blur, `dependenciesChanged` emits with updated deps for the target step

### Edge Condition Picker
- Small floating popup at the edge midpoint when creating/editing an edge
- Three options: ON_SUCCESS (default), ON_FAILURE, ALWAYS
- Each option has icon + label
- Click outside → confirms selection
- Re-clicking an existing edge's label also opens this picker for editing

### Node Click → StepFormDialog
- Single click on a node body (not port) emits `stepSelected`
- Parent component (`JobDetailComponent`) opens the existing `StepFormDialog` via MatDialog
- No change to StepFormDialog — it receives the same `StepFormData` as today

### Delete Node with Dependency Awareness
- Click × button on node → calls parent's `deleteNode(stepId)`
- Parent checks: does any other step depend on this step? (scan dependencies array)
  - If yes: show confirmation dialog "Deleting 'X' will remove it from Y steps that depend on it. Continue?"
  - If no: standard delete confirmation
- On confirm, emits `stepDeleted` → parent calls API, reloads job

### Auto-Save Strategy
- Edge changes are batched: user can draw multiple edges before saving
- "Save Dependencies" button in canvas toolbar (top-right)
- On click, iterates all steps with changed dependencies, calls PUT endpoint for each
- Dirty indicator (• dot next to save button) when unsaved changes exist
- **Navigation guard:** Uses existing `FormGuardService` — marks dirty on edge change

## Component 2: DagNodeComponent

**Selector:** `<app-dag-node>`
**Purpose:** Renders a single step node; handles drag-to-reposition and port interactions

### Inputs / Outputs
```typescript
@Input() node!: DagNodeModel;
@Input() readOnly = false;           // run-view mode: no ports, no delete
@Output() nodeDragEnd = new EventEmitter<{ stepId: number; position: { x, y } }>();
@Output() edgeStart = new EventEmitter<{ stepId: number; portX: number; portY: number }>();
@Output() edgeTargetHover = new EventEmitter<number>();    // stepId being hovered as target
@Output() nodeClick = new EventEmitter<number>();          // stepId
@Output() deleteRequested = new EventEmitter<number>();    // stepId
```

### Template Structure
```html
<div class="dag-node" [class.read-only]="readOnly" [style.left.px]="node.position.x" [style.top.px]="node.position.y">
  <div class="status-ring" [style.borderColor]="statusColor"></div>   <!-- run-view only -->
  <div class="node-body" (click)="onBodyClick()">
    <mat-icon>{{ iconFor(node.stepType) }}</mat-icon>
    <div class="node-labels">
      <span class="step-name">{{ node.stepName }}</span>
      <span class="step-type">{{ node.stepType }}</span>
    </div>
  </div>
  @if (!readOnly) {
    <div class="port port-output" (mousedown)="onPortDrag($event, 'output')">●</div>
    <div class="port port-input" (mouseenter)="edgeTargetHover.emit(node.stepId!)">●</div>
    <button mat-icon-button class="delete-btn" (click)="deleteRequested.emit(node.stepId!)">✕</button>
  }
</div>
```

## Component 3: RunDagCanvasComponent (run-view mode)

**Selector:** `<app-run-dag-canvas>`
**Mode:** Read-only — no editing, shows live status

### Inputs / Outputs
```typescript
@Input() run!: JobRunDetail;         // from RunDetailComponent polling
@Input() stepDependencies!: Map<number, StepDependency[]>;  // pre-fetched from job definition
```

No outputs. Purely presentational.

### Differences from Edit Canvas
- No port handles, no edge creation, no delete buttons
- Nodes colored by status using `STATUS_COLOR_VARS` from run-timeline (status ring border + subtle background tint)
- Running nodes have a pulsing animation on the status ring
- Edges colored based on whether the condition was met:
  - Green if upstream step succeeded and edge is ON_SUCCESS
  - Red if upstream step failed and edge is ON_FAILURE
  - Grey for untraversed edges (upstream not yet started)
- No pan/zoom controls — auto-fits to viewport
- Step names clickable → triggers `viewStepLog()` via parent component

### Status Updates
- Parent (`RunDetailComponent`) polls every 3 seconds, passes updated `JobRunDetail` as input
- Component compares previous status per step; if changed, updates node visual class
- Uses Angular's default change detection (no OnPush needed since polling triggers it)

## Service: DagLayoutService

**Purpose:** Computes initial node positions using a layered top-to-bottom layout.

### Algorithm
1. **Topological sort** of the DAG to determine layers
2. **Layer assignment:**
   - Root nodes (no dependencies) → layer 0
   - Each subsequent node → max(layer of dependencies) + 1
3. **Position within layer:** Distribute nodes evenly horizontally
4. **Spacing constants:**
   - `HORIZONTAL_GAP = 80px` between nodes in same layer
   - `VERTICAL_GAP = 120px` between layers
   - Node width: ~160px, height: ~72px
5. **Padding:** 40px from canvas edges

### Method Signature
```typescript
@Injectable({ providedIn: 'root' })
export class DagLayoutService {
  layout(nodes: DagNodeModel[]): void;  // mutates node.position in place
}
```

## Service: CycleDetectorService

**Purpose:** Prevents circular dependencies before they're created.

### Algorithm
- **DFS with coloring** (white/gray/black) on the dependency graph
- Before adding edge A→B, temporarily add it and check for back edges
- O(V + E) per check — acceptable for <100 steps

### Method Signature
```typescript
@Injectable({ providedIn: 'root' })
export class CycleDetectorService {
  wouldCreateCycle(
    stepId: number,           // the step getting a new dependency
    dependsOnStepId: number,  // the step it would depend on
    existingEdges: Map<number, number[]>   // stepId → [dependsOnStepIds]
  ): boolean;

  hasCycle(nodes: DagNodeModel[]): boolean;  // full graph check
}
```

## Integration Points

### JobDetailComponent Changes
1. **Steps tab:** Add a view toggle (list icon ↔ canvas icon) in the steps header
2. **On canvas mode:**
   - Load job steps + fetch dependencies for each step via new `JobService.getStepDependencies(jobId, stepId)`
   - Merge into `JobStepWithDependencies[]` array
   - Pass to `<app-dag-canvas>` component
3. **On `dependenciesChanged`:** Call PUT endpoint, reload job on success
4. **On `stepSelected`:** Open existing `StepFormDialog` (no change to dialog)
5. **On `addStepRequested`:** Open existing `StepPaletteComponent` dialog

### RunDetailComponent Changes
1. **After run timeline:** Add `<app-run-dag-canvas>` component below the existing `RunTimelineComponent`
2. **Fetch dependencies:** On load, fetch job definition to get step dependency structure (via job ID from run detail)
3. **Pass to canvas:** Provide `run` and `stepDependencies` as inputs

### JobService Extensions
```typescript
// New methods in job.service.ts
getStepDependencies(jobId: number, stepId: number): Observable<ApiResponse<StepDependency[]>> {
  return this.http.get<ApiResponse<StepDependency[]>>(`${this.api}/jobs/${jobId}/steps/${stepId}/dependencies`);
}

setStepDependencies(jobId: number, stepId: number, deps: StepDependencyRequest[]): Observable<ApiResponse<void>> {
  return this.http.put<ApiResponse<void>>(`${this.api}/jobs/${jobId}/steps/${stepId}/dependencies`, deps);
}
```

## CSS Custom Properties (Theme Integration)

Reuse existing status color variables. Add canvas-specific variables:

```scss
// In app.scss or theme file
:root {
  --dag-canvas-bg: var(--surface, #fafafa);
  --dag-canvas-grid-color: var(--outline-variant, #e0e0e0);
  --dag-node-bg: var(--surface-container, #ffffff);
  --dag-node-border: var(--outline, #bdbdbd);
  --dag-edge-color: var(--on-surface-variant, #616161);
  --dag-port-color: var(--primary, #6750a4);
  --dag-port-hover: var(--tertiary, #e2aef7);
}

// Dark mode overrides (already partially handled by existing dark mode)
[data-theme="dark"] {
  --dag-canvas-bg: var(--surface, #1c1b1f);
  --dag-canvas-grid-color: var(--outline-variant, #49454f);
  --dag-node-bg: var(--surface-container, #29272f);
  --dag-node-border: var(--outline, #49454f);
  --dag-edge-color: var(--on-surface-variant, #cac4d0);
}
```

## File Structure Summary

```
orchestrator-ui/src/app/
├── core/
│   ├── models/
│   │   └── job.model.ts          # ADD: StepDependency, EdgeCondition, JobStepWithDependencies
│   └── services/
│       └── job.service.ts        # ADD: getStepDependencies(), setStepDependencies()
├── features/jobs/
│   ├── dag-canvas/               # NEW directory
│   │   ├── dag-canvas.component.ts
│   │   ├── dag-canvas.component.html
│   │   ├── dag-canvas.component.scss
│   │   ├── dag-node.component.ts
│   │   ├── dag-node.component.html
│   │   ├── dag-node.component.scss
│   │   ├── dag-edge-renderer.ts
│   │   ├── edge-condition-picker.component.ts
│   │   ├── edge-condition-picker.component.html
│   │   ├── edge-condition-picker.component.scss
│   │   ├── run-dag-canvas.component.ts
│   │   ├── run-dag-canvas.component.html
│   │   ├── run-dag-canvas.component.scss
│   │   └── services/
│   │       ├── dag-layout.service.ts
│   │       └── cycle-detector.service.ts
│   ├── dag-canvas-stub/          # DELETE (replaced by dag-canvas)
│   └── job-detail/
│       ├── job-detail.component.ts    # MODIFY: canvas toggle, event handlers
│       └── job-detail.component.html  # MODIFY: add canvas view in Steps tab
├── features/runs/
│   └── run-detail/
│       ├── run-detail.component.ts    # MODIFY: fetch deps, pass to run-dag-canvas
│       └── run-detail.component.html  # MODIFY: add <app-run-dag-canvas>
└── app.routes.ts                 # MODIFY: route /jobs/:id/canvas → DagCanvasComponent (or remove, integrated into job-detail)
```
