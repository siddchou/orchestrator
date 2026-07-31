# Phase 3 UI — Implementation Status

**Date:** 2026-07-31
**Branch:** `plan3-phase3-ui`

## Summary

18 of 20 tasks completed. The full DAG canvas feature is functional: edit-mode canvas with pan/zoom, edge drawing, cycle detection, auto-layout, and a read-only run-view canvas with status-colored nodes and live polling integration.

## Completed Tasks (1–18)

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Extend Frontend Models & JobService | ✅ Done | (prior session) |
| 2 | Cycle Detection Service | ✅ Done | (prior session) |
| 3 | DAG Layout Service | ✅ Done | (prior session) |
| 4 | Edge Renderer (Pure Function) | ✅ Done | (prior session) |
| 5 | DagNodeComponent | ✅ Done | (prior session) |
| 6 | Edge Condition Picker Component | ✅ Done | (prior session) |
| 7 | DagCanvasComponent — Core Canvas (Pan/Zoom + SVG Layer) | ✅ Done | (prior session) |
| 8 | DagCanvasComponent — Node Orchestration + Auto-Layout | ✅ Done | (prior session) |
| 9 | DagCanvasComponent — Edge Drawing + Condition Labels | ✅ Done | (prior session) |
| 10 | DagCanvasComponent — Add-Edge Interaction | ✅ Done | (prior session) |
| 11 | DagCanvasComponent — Save Dependencies | ✅ Done | (prior session) |
| 12 | DagCanvasComponent — Node Click → StepFormDialog + Add/Delete Steps | ✅ Done | (prior session) |
| 13 | JobDetailComponent Integration — List/Canvas Toggle | ✅ Done | `219f151` |
| 14 | Route Update — Remove Stub | ✅ Done | `b79bbac` |
| 15 | RunDagCanvasComponent — Read-Only Canvas | ✅ Done | `5622989` |
| 16 | RunDetailComponent Integration | ✅ Done | `3bfa699` |
| 17 | Dark Mode Styling | ✅ Done | `fa43dc5` |
| 18 | Unit Tests — Services & Pure Functions | ✅ Verified | (specs pre-existed, all passing) |

### Test Coverage

- **86 unit tests** pass across 7 spec files in `dag-canvas/`
- Covers: cycle detection (linear, diamond, cycle, empty), layout positions (chain, diamond, fan-out), edge path rendering, component inputs/events, dark mode variable overrides
- Run with: `npx ng test orchestrator-ui --no-watch --include='**/dag-canvas/**/*.spec.ts'`

## Incomplete Tasks (19–20)

| # | Task | Status | Blocker |
|---|------|--------|---------|
| 19 | E2E Test — Diamond DAG Build & Save | ⏸️ Blocked | No e2e framework installed (no Playwright/Cypress/Puppeteer in project) |
| 20 | E2E Test — Run-View Canvas Live Updates | ⏸️ Blocked | Same — requires e2e framework + running backend API |

### Why Tasks 19–20 Are Blocked

The project has **no browser automation tooling**. `package.json` includes only Vitest (unit tests via Angular's built-in test runner). Writing the specified E2E tests requires:

1. Installing an e2e framework (**Playwright recommended** — `npx playwright init --lang typescript`)
2. Configuring a test server or mock API for job creation, step management, and dependency CRUD
3. Setting up authentication fixtures (the backend uses JWT)
4. Creating page object models for the Job Detail and Run Detail pages

This is infrastructure work that benefits the whole project but is outside the scope of the DAG canvas feature itself.

### To Unblock Tasks 19–20

```bash
# In orchestrator-ui/
npx playwright init --lang typescript
npm install -D @playwright/test
```

Then configure `playwright.config.ts` with:
- Base URL pointing to the dev server (`ng serve`)
- API interception for job/step CRUD endpoints (or a test backend instance)
- Auth fixture that obtains a JWT token

## Key Files Created/Modified

### New Components
- `dag-node.component.{ts,html,scss}` — Node card with ports, status ring, delete button
- `edge-condition-picker.component.{ts,html,scss}` — Floating condition selector (ON_SUCCESS/ON_FAILURE/ALWAYS)
- `dag-canvas.component.{ts,html,scss}` — Edit-mode canvas with pan/zoom, edge drawing, save
- `run-dag-canvas.component.{ts,html,scss}` — Read-only run-view canvas with status colors

### New Services & Utilities
- `cycle-detector.service.ts` — DFS-based cycle detection (white/gray/black)
- `dag-layout.service.ts` — Layered top-to-bottom layout algorithm
- `dag-edge-renderer.ts` — Cubic bezier edge path computation (pure function)

### Integration Points
- `job-detail.component.{ts,html}` — List/Canvas view toggle, dependency loading
- `run-detail.component.{ts,html,scss}` — RunDagCanvasComponent with step ID mapping
- `app.routes.ts` — Removed `/jobs/:id/canvas` stub route
- `styles.scss` — Dark mode CSS custom properties for all DAG canvas components

### Deleted
- `dag-canvas-stub/` directory (3 files) — placeholder component removed

## Architecture Decisions

1. **Step ID bridge** — Job definition uses `stepId`, run data uses `runStepId`. Mapped via shared `stepOrder` field in RunDetailComponent.
2. **Dark mode via CSS custom properties** — Global `[data-theme="dark"]` overrides in `styles.scss`. Component SCSS uses variables with light-mode fallbacks (`var(--dag-canvas-bg, #fafafa)`).
3. **Layout algorithm** — Layered: roots at layer 0, each node at `max(dependency layers) + 1`. Horizontal spacing distributes nodes evenly within a layer.
4. **Edge rendering** — Cubic bezier curves with vertical control points. Source output port (bottom-center) to target input port (top-center). Arrowhead marker on SVG `<defs>`.
5. **Pan/zoom** — CSS `transform: translate() scale()` with cursor-centered zoom. Range clamped to 0.3×–2×. Shift+drag or middle-mouse for pan.
