# Phase 2 — UI/UX Genericization: Overview (Audit)

## Goal

Verify that the orchestrator UI is **step-type agnostic**: any step type registered via Phase 1's SPI should be immediately usable in the UI with zero Angular code changes. Confirm team-based multi-tenancy works end-to-end. Validate dark mode, run timeline, and DAG canvas stub are production-ready.

## Scope (Grounded in Findings)

All Phase 2 features described in the original plan **are implemented**. This document reframes scope as an audit with three tracks:

| Track | Description | Status |
|-------|-------------|--------|
| **A — Dynamic Forms** | DynamicField, DynamicStepForm, StepPalette, StepFormDialog | Implemented. Needs gap analysis + polish |
| **B — Multi-Tenancy** | TEAM/USER_TEAM tables, TeamSwitcher, server-side scoping | DB migration done. Frontend UI done. Backend scoping needs verification |
| **C — Polish** | Dark mode, RunTimeline, DAG canvas stub | All implemented. Dark mode complete. Timeline complete. Stub adequate |

## Assumptions

1. Phase 1 SPI contract (`StepConfigSchema`, `FieldDefinition`, `FieldType`) is stable — no breaking changes expected mid-Phase 2.
2. The backend `/api/step-types` endpoint returns consistent schemas across deployments.
3. Team-based multi-tenancy uses server-side scoping (JWT claim or request attribute) — not client-side filtering alone.
4. Angular 21 + Material M3 is the target stack; no framework migration planned.
5. DAG canvas (visual graph editor) is deferred to Phase 2b — stub route suffices for now.

## Table of Contents

| File | Description |
|------|-------------|
| `phase2-code-review-findings.md` | Step 0 code review — actual state of the codebase |
| `phase2-00-overview.md` | This file — scope, assumptions, effort estimates |
| `phase2-01-component-design.md` | Component design docs for all Phase 2 components (as-built) |
| `phase2-02-task-breakdown.md` | PR-sized tasks with dependencies and DoD |
| `phase2-03-multi-tenancy-migration.md` | Multi-tenancy migration status + remaining backend work |
| `phase2-04-edge-cases-and-failure-modes.md` | Edge case matrix — handled vs. unhandled |
| `phase2-05-testing-plan.md` | Unit test coverage gaps and E2E plan |

## Effort Estimate (Remaining Work)

| Task | Story Points | Rationale |
|------|-------------|-----------|
| Gap analysis: step palette hardcoded metadata → API-driven | 2 | Replace hardcoded icon/description map with schema `displayName` + default icon fallback |
| Edge case hardening: unsupported FieldType, team switch mid-edit, concurrent edits | 3 | Defensive code paths for scenarios not yet handled |
| Server-side team scoping verification | 5 | Ensure all job/run/credential APIs filter by team from JWT — may require backend changes |
| Test coverage gaps (JobDetail, StepFormDialog, ThemeService, FormGuard) | 5 | Complex components with no tests yet |
| E2E smoke test suite | 3 | Critical path: login → create job → add step → save → run → view timeline |
| Dark mode polish (missing element overrides) | 1 | Audit rendered elements in dark mode for contrast issues |
| **Total** | **~19 SP** | Down from original ~40 SP because implementation is largely complete |

## Deferred Items

- **DAG canvas** — visual graph editor with node dragging, edge rendering, zoom/pan. Deferred to Phase 2b. Stub route at `/jobs/:id/canvas` provides a placeholder card.
- **i18n / localization** — not scoped for Phase 2. All user-facing strings are hardcoded English.
- **Accessibility (WCAG)** — no ARIA labels, keyboard navigation beyond defaults, or screen reader testing planned for this phase.
