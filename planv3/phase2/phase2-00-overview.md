# Phase 2 — UI/UX Genericization: Overview

## Goal

Make the orchestrator UI **step-type agnostic**: any step type registered via Phase 1's SPI should be immediately usable in the UI with zero Angular code changes. Add team-based multi-tenancy so multiple teams can share an instance without seeing each other's jobs. Ship basic polish (dark mode, run timeline).

## Scope — Grounded in Code Review Findings

### In Scope

| Area | What Changes | Why |
|------|-------------|-----|
| **Dynamic forms hardening** | Improve `DynamicFieldComponent` and `DynamicStepFormComponent`: add validation feedback, unsupported-type fallback UI, credential picker for SECRET_REF | Existing components work but lack polish; SECRET_REF is a free-text input with no validation against actual credentials |
| **Step palette API-driven** | Remove hardcoded `STEP_TYPE_META` from `step-palette.ts`; derive icon/description from API or use sensible defaults | Hardcoded metadata breaks when new step types are registered without frontend changes |
| **Team multi-tenancy** | New `TEAM`, `USER_TEAM` tables; `JOB_DEFINITION.team_id` FK; team switcher in top nav; server-side job filtering by team | Exit criterion: two teams see only their own jobs |
| **Dark mode** | Theme toggle button, dark CSS variable values, `localStorage` persistence | Design tokens already use CSS variables — swap values, don't rewrite styles |
| **Run timeline** | Horizontal Gantt-like bar chart per step in run detail, color-coded by status | Data already exists in `JOB_RUN_STEP(started_at, ended_at, status)` |
| **DAG canvas stub route** | `/jobs/:id/canvas` → placeholder component with TODO comment referencing Phase 3 | Signals future capability without implementing it |
| **Unit tests** | First round of Angular unit tests for dynamic form components | Vitest is configured but no tests exist — establish the pattern |

### Deferred to "Phase 2b" (Post-Phase 3)

| Item | Why Deferred |
|------|-------------|
| **DAG canvas implementation** | Requires Phase 3's `dependsOn` dependency model in `JOB_STEP`. The canvas is meaningless without edges. Only the stub route ships in Phase 2. |
| **Team-level RBAC** (team-scoped roles distinct from global ADMIN/OPERATOR/VIEWER) | Adds complexity with limited ROI at this stage. Global roles + team membership suffice for v1. |
| **Job templates / cloning** | Depends on multi-tenancy being stable first. |

## Assumptions

- **[ASSUMED]** The backend runs Oracle (SQL syntax in migrations uses `VARCHAR2`, `NUMBER GENERATED ALWAYS AS IDENTITY`, `SYSTIMESTAMP`). All new SQL follows this dialect.
- **[ASSUMED]** The Spring Boot app serves the Angular build from `../../src/main/resources/static` (confirmed by angular.json `outputPath`). No separate frontend deployment.
- **[ASSUMED]** The JWT returned by `/api/auth/login` contains only `{accessToken, role}`. Team context will be added as a new claim or derived server-side from session state. **Decision: derive server-side** — the input plan says "prefer server-side to avoid trusting client-supplied tenant IDs."
- **[ASSUMED]** The active team for a user is stored in HTTP session (or a short-lived token claim refreshed on team switch), not passed as a client-controlled query parameter.
- **[ASSUMED]** Existing jobs (created before multi-tenancy) are assigned to a "Default" team so they remain accessible during and after migration.
- **[ASSUMED]** The Inter font family is loaded via the HTML `<head>` or a CDN link in `index.html`. No change needed for dark mode.

## Effort Estimate

| Task | Story Points | Complexity Driver |
|------|-------------|-------------------|
| Harden dynamic forms (validation, credential picker) | 5 | SECRET_REF → credential dropdown requires new API call + state |
| API-driven step palette | 2 | Remove hardcoded map, handle missing icon gracefully |
| TEAM + USER_TEAM migration + backfill | 3 | Oracle DDL, nullable FK transition, data migration |
| Backend team-scoped queries | 5 | Touches job/run repositories, security filter, JWT/session |
| Team switcher UI component | 3 | New nav component, active-team state management |
| Dark mode toggle + dark palette | 5 | CSS variable overrides for all tokens, Material M3 dark theme |
| Run timeline component | 5 | New visualization, time-scale calculation, responsive layout |
| DAG canvas stub route | 1 | Single component with placeholder text |
| Unit tests (dynamic forms) | 5 | First Angular test setup + component tests |
| **Total** | **34** | ~8-10 story points per sprint = **~2 sprints** |

## Table of Contents

1. [phase2-code-review-findings.md](phase2-code-review-findings.md) — Codebase state at planning time
2. [phase2-00-overview.md](phase2-00-overview.md) — This file: scope, assumptions, estimates
3. [phase2-01-component-design.md](phase2-01-component-design.md) — Component-level design for all new/changed components
4. [phase2-02-task-breakdown.md](phase2-02-task-breakdown.md) — PR-sized tasks with DoD and dependencies
5. [phase2-03-multi-tenancy-migration.md](phase2-03-multi-tenancy-migration.md) — Flyway SQL + rollback strategy
6. [phase2-04-edge-cases-and-failure-modes.md](phase2-04-edge-cases-and-failure-modes.md) — Edge cases and required handling
7. [phase2-05-testing-plan.md](phase2-05-testing-plan.md) — Unit tests, E2E test, regression checklist

## Exit Criteria

1. A step type registered in Phase 1 is immediately usable end-to-end in the UI with zero Angular changes beyond what Phase 1 already required (the `getConfigSchema()` implementation).
2. Two teams can each create jobs and see only their own jobs.
3. Dark mode toggle works and persists across page reloads.
4. Run detail page shows a horizontal timeline alongside the step table.
5. All new components have unit tests covering happy path + one error path each.
