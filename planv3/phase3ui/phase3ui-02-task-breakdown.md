# Phase 3 UI — Task Breakdown

## Task 1: Extend Frontend Models & JobService

**Files Touched:** `core/models/job.model.ts`, `core/services/job.service.ts`
**Definition of Done:**
- `StepDependency`, `EdgeCondition`, `JobStepWithDependencies` interfaces added to job.model.ts
- `getStepDependencies()` and `setStepDependencies()` methods on JobService
- TypeScript compiles cleanly; no breaking changes to existing consumers

**Test to Add:** Unit test for new JobService methods — verify correct URL construction
**Depends On:** Nothing

---

## Task 2: Cycle Detection Service

**Files Touched:** `features/jobs/dag-canvas/services/cycle-detector.service.ts` (+ spec)
**Definition of Done:**
- `wouldCreateCycle(stepId, dependsOnStepId, existingEdges)` returns boolean
- `hasCycle(nodes)` performs full graph cycle check
- Handles empty graph, single node, linear chain, diamond shape

**Test to Add:** Unit tests for all 4 graph shapes + cycle insertion detection
**Depends On:** Task 1 (needs StepDependency type)

---

## Task 3: DAG Layout Service

**Files Touched:** `features/jobs/dag-canvas/services/dag-layout.service.ts` (+ spec)
**Definition of Done:**
- `layout(nodes)` computes top-to-bottom layered positions
- Handles empty input, single node, linear chain, diamond shape, wide fan-out (10+ nodes in one layer)
- Positions fit within a reasonable canvas area (< 2000px width for 15 nodes)

**Test to Add:** Unit tests verifying position output for known graph shapes
**Depends On:** Task 1

---

## Task 4: Edge Renderer (Pure Function)

**Files Touched:** `features/jobs/dag-canvas/dag-edge-renderer.ts` (+ spec)
**Definition of Done:**
- `computeEdgePath(sourceNode, targetNode)` returns SVG path `d` attribute string
- Cubic bezier curves from source output port to target input port
- Returns midpoint coordinates for label placement
- Handles nodes at any relative position (above/below/left/right)

**Test to Add:** Unit tests with fixed node positions → assert expected path strings
**Depends On:** Nothing

---

## Task 5: DagNodeComponent

**Files Touched:** `features/jobs/dag-canvas/dag-node.component.{ts,html,scss}` (+ spec)
**Definition of Done:**
- Renders step icon (reuses `iconFor()` from step-palette), name, type label
- Output port (top-right) and input port (bottom-left) visible in edit mode
- Ports hidden in read-only mode
- Delete button on hover (edit mode only)
- Status ring border in run-view mode (colored by status)
- Emits correct events: nodeClick, edgeStart, edgeTargetHover, deleteRequested

**Test to Add:** Component unit test — verify template renders icon/name/ports; event emissions
**Depends On:** Task 1

---

## Task 6: Edge Condition Picker Component

**Files Touched:** `features/jobs/dag-canvas/edge-condition-picker.component.{ts,html,scss}` (+ spec)
**Definition of Done:**
- Renders three options: ON_SUCCESS (✓), ON_FAILURE (✗), ALWAYS (∞)
- Positionable absolutely at any x,y coordinate
- Emits selected value on click
- Closes on selection or outside click

**Test to Add:** Component unit test — verify all 3 options emit correct values
**Depends On:** Task 1

---

## Task 7: DagCanvasComponent — Core Canvas (Pan/Zoom + SVG Layer)

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.{ts,html,scss}` (+ spec)
**Definition of Done:**
- Canvas container with CSS transform for pan/zoom
- Mouse wheel zooms (0.3x–2x), centered on cursor
- Shift+drag or middle-mouse drag pans the canvas
- Zoom controls: + / - / fit buttons in bottom-right corner
- SVG layer renders edges behind node elements
- Grid background pattern (subtle dots or squares)

**Test to Add:** Unit test for zoom/pan state management
**Depends On:** Task 4, Task 5

---

## Task 8: DagCanvasComponent — Node Orchestration + Auto-Layout

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.ts` (extend)
**Definition of Done:**
- On `@Input() steps` change, creates `DagNodeModel[]` and runs layout service
- Renders `<app-dag-node>` for each step at computed positions
- Node drag-to-reposition: mousedown on node body → track delta → update position
- `nodeDragEnd` events persist position in local state

**Test to Add:** Unit test — verify layout is called on steps input change
**Depends On:** Task 3, Task 7

---

## Task 9: DagCanvasComponent — Edge Drawing + Condition Labels

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.ts` (extend)
**Definition of Done:**
- SVG `<path>` elements for each dependency edge
- Path data computed via `dag-edge-renderer.ts`
- Arrowhead marker at target end
- Condition label pill at midpoint (✓/✗/∞ symbols)
- Edges update when nodes are repositioned

**Test to Add:** Visual verification in dev server
**Depends On:** Task 4, Task 8

---

## Task 10: DagCanvasComponent — Add-Edge Interaction

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.ts` (extend)
**Definition of Done:**
- Drag from output port → rubber-band SVG line follows cursor
- Hover over valid target input ports highlights them
- Drop on valid target → opens EdgeConditionPicker at drop point
- Selected condition creates edge in local state
- Cycle detection prevents circular dependencies with snackbar feedback
- Duplicate edge detection (same source→target pair)

**Test to Add:** Unit test for cycle detection integration + duplicate prevention
**Depends On:** Task 2, Task 6, Task 9

---

## Task 11: DagCanvasComponent — Save Dependencies

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.ts` (extend), `job-detail.component.{ts,html}`
**Definition of Done:**
- "Save Dependencies" button in canvas toolbar
- Dirty indicator when local state differs from server state
- On save: batches PUT requests for all steps with changed dependencies
- Reloads job on success, shows snackbar
- FormGuardService integration — marks dirty on edge change

**Test to Add:** Unit test verifying emit behavior on save
**Depends On:** Task 10

---

## Task 12: DagCanvasComponent — Node Click → StepFormDialog + Add/Delete Steps

**Files Touched:** `features/jobs/dag-canvas/dag-canvas.component.ts` (extend), `job-detail.component.{ts,html}`
**Definition of Done:**
- Node click emits step data → parent opens existing StepFormDialog
- "Add Step" button in canvas toolbar → parent opens existing StepPaletteComponent
- New steps appear as nodes at next available layout position
- Delete node with dependency-aware confirmation dialog

**Test to Add:** Unit test for event flow from canvas → job-detail
**Depends On:** Task 8, Task 10

---

## Task 13: JobDetailComponent Integration — List/Canvas Toggle

**Files Touched:** `features/jobs/job-detail/job-detail.component.{ts,html}`
**Definition of Done:**
- Steps tab header has view toggle button (list ↔ canvas icons)
- List view = existing CDK drag-drop list (unchanged)
- Canvas view = `<app-dag-canvas>` component with loaded dependencies
- On switching to canvas: fetches dependencies for all steps via JobService
- Merges into `JobStepWithDependencies[]` and passes as input

**Test to Add:** Visual verification in dev server
**Depends On:** Task 1, Tasks 7–12

---

## Task 14: Route Update — Replace Stub

**Files Touched:** `app.routes.ts`, delete `dag-canvas-stub/` directory
**Definition of Done:**
- `/jobs/:id/canvas` route removed (canvas is now integrated into job-detail Steps tab)
- OR: route redirects to `/jobs/:id` with a fragment/query param for canvas view
- DagCanvasStubComponent files deleted

**Test to Add:** Navigate to old URL → confirms redirect or integration works
**Depends On:** Task 13

---

## Task 15: RunDagCanvasComponent — Read-Only Canvas

**Files Touched:** `features/jobs/dag-canvas/run-dag-canvas.component.{ts,html,scss}` (+ spec)
**Definition of Done:**
- Renders nodes and edges in read-only mode (no ports, no delete buttons)
- Node status ring colored by step status using existing color conventions
- Running steps have pulsing animation on status ring
- Edge colors reflect traversal state (green/red/grey)
- Auto-fits to viewport (no manual pan/zoom)

**Test to Add:** Component unit test — verify status colors applied correctly
**Depends On:** Tasks 3, 4, 5

---

## Task 16: RunDetailComponent Integration

**Files Touched:** `features/runs/run-detail/run-detail.component.{ts,html}`
**Definition of Done:**
- `<app-run-dag-canvas>` rendered below the existing timeline visualization
- Fetches job definition to get step dependency structure (via jobId from run)
- Passes `run` and `stepDependencies` as inputs
- 3-second polling updates node colors automatically

**Test to Add:** Visual verification in dev server
**Depends On:** Task 15, Task 1

---

## Task 17: Dark Mode Styling

**Files Touched:** All `.scss` files in `dag-canvas/` directory
**Definition of Done:**
- Canvas background, node cards, edge colors adapt to dark mode
- Uses existing CSS custom properties (`--surface`, `--outline`, etc.)
- Verified visually in both light and dark themes

**Test to Add:** Visual verification (no automated test needed)
**Depends On:** Tasks 5, 7, 15

---

## Task 18: Unit Tests — Services & Pure Functions

**Files Touched:** `cycle-detector.service.spec.ts`, `dag-layout.service.spec.ts`, `dag-edge-renderer.spec.ts`
**Definition of Done:**
- Cycle detector: tests for linear, diamond, cycle, empty graph
- Layout service: position assertions for 3-node chain, diamond, fan-out (5 nodes in one layer)
- Edge renderer: path string assertions for known node positions

**Test to Add:** All spec files listed above
**Depends On:** Tasks 2, 3, 4

---

## Task 19: E2E Test — Diamond DAG Build & Save

**Files Touched:** `e2e/tests/dag-canvas.spec.ts` (new)
**Definition of Done:**
- Creates a new job via UI
- Switches to canvas view
- Adds 4 steps (A, B, C, D) via StepPaletteComponent
- Draws edges: A→B, A→C, B→D, C→D (diamond shape)
- Sets edge conditions (mix of ON_SUCCESS and ALWAYS)
- Clicks Save Dependencies
- Verifies API calls made correctly (intercept PUT requests)
- Reloads page → canvas renders diamond shape with correct edges

**Test to Add:** Full E2E test as described
**Depends On:** Tasks 10, 11, 13

---

## Task 20: E2E Test — Run-View Canvas Live Updates

**Files Touched:** `e2e/tests/run-dag-canvas.spec.ts` (new)
**Definition of Done:**
- Starts a job run with multiple steps
- Navigates to run detail page
- Verifies run-view canvas renders all nodes and edges
- Waits for steps to complete (or mocks polling responses)
- Verifies node status colors change from PENDING → RUNNING → SUCCESS/FAILED

**Test to Add:** Full E2E test as described
**Depends On:** Task 15, Task 16
