# Phase 3 UI — Overview: Visual DAG Canvas

## Goal

Replace the linear step-list job editor with a drag-and-drop DAG canvas where nodes are steps and edges are dependencies with condition labels. Add a read-only run-view canvas showing live per-step status during execution.

## Scope

### In Scope
1. **Job-edit DAG canvas** — replaces the "Steps" tab in `JobDetailComponent`
   - Nodes represent job steps, rendered with step-type icons from Phase 1 metadata
   - Edges represent dependencies (A depends on B), labeled with condition (ON_SUCCESS/ON_FAILURE/ALWAYS)
   - Drag to create edges between nodes
   - Click a node → opens existing `StepFormDialog` (which wraps `DynamicStepFormComponent`)
   - Add new steps via existing `StepPaletteComponent` dialog
   - Delete nodes with dependency-aware confirmation
   - Auto-layout on load, manual drag repositioning

2. **Run-view DAG canvas** — read-only variant in `RunDetailComponent`
   - Same node/edge rendering but non-interactive
   - Node colors reflect live step status (reusing existing color conventions)
   - Updates via existing 3-second polling mechanism

3. **Model extensions** — frontend types to represent dependency edges

4. **Route integration** — replace the stub at `/jobs/:id/canvas` with the real canvas; also integrate into the Steps tab as a toggle (list ↔ canvas view)

### Out of Scope
- Redesigning `DynamicStepFormComponent` or `StepPaletteComponent` — reused as-is
- Backend API changes — Phase 3 backend is complete
- Sub-workflow composition (`SUB_JOB`) — deferred per Phase 3 plan
- Multi-canvas collaboration, undo/redo stack

## Assumptions

- **[ASSUMED]** The backend GET `/api/jobs/{id}` response does NOT embed dependency edges in the step array. We must fetch dependencies separately via `GET /{id}/steps/{stepId}/dependencies` for each step after loading the job.
- **[ASSUMED]** No dedicated step-status SSE stream exists. Run-view canvas will use the existing 3-second polling pattern from `RunDetailComponent`.
- **[ASSUMED]** We will implement our own SVG-based DAG rendering without a third-party graph library (no ngx-graph, d3, cytoscape). The canvas uses absolute-positioned nodes connected by SVG `<path>` bezier curves.
- **[ASSUMED]** Auto-layout will use a simple layered (Sugiyama-style) top-to-bottom algorithm implemented in TypeScript, not an external layout engine like dagre/elkjs.
- **[ASSUMED]** The canvas replaces the linear list as the default view for jobs with 2+ steps; single-step jobs show the list view only.

## Reuse vs New Build

| Component | Status | Action |
|-----------|--------|--------|
| `DynamicStepFormComponent` | Existing | **Reuse unchanged** — opened via StepFormDialog when node clicked |
| `StepPaletteComponent` | Existing | **Reuse unchanged** — opened as dialog for adding new steps |
| `StepFormDialog` | Existing | **Reuse unchanged** — wraps DynamicStepFormComponent, handles save |
| `StatusBadge` | Existing | **Reuse unchanged** — used in run-view canvas nodes |
| `iconFor()` helper from step-palette | Existing | **Reuse unchanged** — node icons in canvas |
| `STATUS_COLOR_VARS` from run-timeline | Existing | **Reuse unchanged** — node status colors in run-view |
| `JobService.listStepTypes()` | Existing | **Reuse unchanged** — for step-type metadata |
| DAG Canvas Component | **New** | Main canvas with nodes, edges, pan/zoom |
| DAG Node Component | **New** | Individual step node rendering |
| DAG Edge Renderer | **New** | SVG edge path computation + condition labels |
| Edge Condition Picker | **New** | Dropdown for selecting ON_SUCCESS/Failure/Always |
| DAG Layout Service | **New** | Auto-layout algorithm |
| Cycle Detection Service | **New** | Prevents circular dependencies |
| Run-View Canvas Component | **New** | Read-only canvas with live status |
| Dependency API methods on JobService | **New** | `getStepDependencies()`, `setStepDependencies()` |

## Effort Estimate

| Task Group | Stories | Complexity | Est. Hours |
|------------|---------|------------|------------|
| Model + Service extensions | 1–2 | Low | 4 |
| DAG Layout + Cycle Detection | 2 | Medium-High | 8 |
| Canvas core (pan/zoom, SVG layer) | 2 | Medium | 6 |
| Node rendering + drag repositioning | 2 | Medium | 6 |
| Edge drawing + condition labels | 2 | Medium | 6 |
| Add-edge interaction (drag node-to-node) | 1–2 | Medium-High | 8 |
| Edge condition picker | 1 | Low-Medium | 4 |
| Node click → StepFormDialog integration | 1 | Low | 3 |
| Delete node with dependency check | 1 | Low-Medium | 3 |
| Run-view canvas (read-only + live status) | 2 | Medium | 6 |
| JobDetailComponent integration (tab toggle) | 1 | Low-Medium | 4 |
| RunDetailComponent integration | 1 | Low | 3 |
| Unit tests | — | Medium | 8 |
| E2E tests (diamond DAG + run-view polling) | 2 | High | 8 |
| **Total** | ~15–20 tasks | — | **~77 hours (~2 weeks)** |

## Table of Contents

1. [phase3ui-code-review-findings.md](./phase3ui-code-review-findings.md) — Code review findings
2. [phase3ui-00-overview.md](./phase3ui-00-overview.md) — This file (scope, assumptions, effort)
3. [phase3ui-01-canvas-component-design.md](./phase3ui-01-canvas-component-design.md) — Full component design
4. [phase3ui-02-task-breakdown.md](./phase3ui-02-task-breakdown.md) — PR-sized task breakdown
5. [phase3ui-03-edge-cases-and-failure-modes.md](./phase3ui-03-edge-cases-and-failure-modes.md) — Edge cases table
6. [phase3ui-04-testing-plan.md](./phase3ui-04-testing-plan.md) — Unit + E2E test plan
