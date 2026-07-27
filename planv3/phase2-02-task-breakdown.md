# Phase 2 — Task Breakdown

## Task List (18 tasks, PR-sized)

### T1: Extend AuthService with team context

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/core/services/auth.service.ts`, `orchestrator-ui/src/app/core/models/api-response.model.ts` |
| **Definition of Done** | `AuthUser` interface extended with `teams: TeamSummary[]` and `activeTeamId?: number`. `AuthService` has `getActiveTeamId()`, `setActiveTeamId(id)`, `getTeams()` methods. Values persisted in sessionStorage alongside token. |
| **Test to Add** | Unit test: `auth.service.spec.ts` — verify team ID persists across `loadFromStorage()` cycle |
| **Depends On** | Nothing (frontend-only, backend endpoint not called yet) |

---

### T2: Create TeamService and Team model

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/core/models/job.model.ts` (add TeamSummary type), new file `orchestrator-ui/src/app/core/services/team.service.ts` |
| **Definition of Done** | `TeamService` has `listMyTeams()`, `setActiveTeam(teamId)`, `getActiveTeam()` methods returning typed Observables. Model includes `TeamSummary { teamId, teamName, role? }`. |
| **Test to Add** | Unit test: mock HttpClient, verify correct URL/path for each method |
| **Depends On** | T1 (model types), T7 (backend endpoints — for integration testing, not compilation) |

---

### T3: Build TeamSwitcherComponent

| Field | Value |
|-------|-------|
| **Files Touched** | New files `orchestrator-ui/src/app/shared/components/team-switcher/team-switcher.{ts,html,scss}`, `orchestrator-ui/src/app/app.ts` (or shell component) |
| **Definition of Done** | Compact dropdown renders team names. Selecting a team calls `TeamService.setActiveTeam()`, updates `AuthService.activeTeamId`, and reloads the page. VIEWER role sees read-only display. Loading state shows spinner, empty state shows "No teams assigned". |
| **Test to Add** | Unit test: verify dialog renders team list from service, emits correct team ID on selection |
| **Depends On** | T1, T2 |

---

### T4: Integrate TeamSwitcher into app shell

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/app.ts`, `orchestrator-ui/src/app/app.html` |
| **Definition of Done** | Team switcher appears in the toolbar/sidenav header. Visible only when user has ≥1 team. Positioned next to user info. |
| **Test to Add** | Visual verification in dev server (no unit test — integration point) |
| **Depends On** | T3 |

---

### T5: Harden DynamicFieldComponent — validation feedback + credential picker

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/shared/components/dynamic-field/dynamic-field.ts`, `dynamic-field.html`, `dynamic-field.scss` |
| **Definition of Done** | 1. `@Input() showError` triggers `<mat-error>` display below invalid fields. 2. SECRET_REF renders as `<mat-select>` populated from `@Input() credentials`. Falls back to text input if no credentials provided. 3. Unknown FieldType shows warning banner alongside the fallback text input. 4. Required indicator styled consistently (red asterisk). |
| **Test to Add** | Unit test: `dynamic-field.spec.ts` — one test per FieldType rendering, plus showError toggles error display, unknown type renders warning |
| **Depends On** | Nothing (existing component) |

---

### T6: Harden DynamicStepFormComponent — schema change detection + validation summary

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.ts`, `dynamic-step-form.html` |
| **Definition of Done** | 1. `ngOnChanges` rebuilds form when `schema` input changes (for step type switching in dialog). 2. `validate()` method marks all controls touched, returns boolean. 3. `toConfig()` returns `{ config, valid }`. 4. Per-field error display wired through `touchedFields` Set. 5. Optional `@Input() credentials` passed to child DynamicFieldComponents. |
| **Test to Add** | Unit test: `dynamic-step-form.spec.ts` — form rebuilds on schema change, validate() marks controls touched, toConfig() converts LIST_STRING to array |
| **Depends On** | T5 (child component changes) |

---

### T7: Backend — TEAM + USER_TEAM tables + Flyway migration V7

| Field | Value |
|-------|-------|
| **Files Touched** | New file `src/main/resources/db/migration/V7__add_multi_tenancy.sql`, new entity classes, new repository interfaces |
| **Definition of Done** | Migration creates TEAM table, USER_TEAM join table, adds nullable `TEAM_ID` to JOB_DEFINITION with FK. Inserts "Default" team row. Backfills all existing jobs to Default team. See [phase2-03-multi-tenancy-migration.md](phase2-03-multi-tenancy-migration.md) for exact SQL. |
| **Test to Add** | Integration test: run migration against test DB, verify TEAM row exists, all JOB_DEFINITION rows have non-null team_id |
| **Depends On** | Nothing |

---

### T8: Backend — Team entity + repository + service layer

| Field | Value |
|-------|-------|
| **Files Touched** | New files `Team.java`, `UserTeam.java`, `TeamRepository.java`, `UserTeamRepository.java`, `TeamService.java` |
| **Definition of Done** | JPA entities for TEAM and USER_TEAM. Repository interfaces with methods: `findByUserId(userId)`, `setActiveTeamInSession(teamId)`. Service layer for team membership CRUD (ADMIN only can create teams and assign users). |
| **Test to Add** | Unit test: `TeamServiceUnitTest` — verify team creation, user assignment, list-my-teams filtering |
| **Depends On** | T7 |

---

### T9: Backend — Team controller endpoints

| Field | Value |
|-------|-------|
| **Files Touched** | New file `api/controller/TeamController.java`, `auth.interceptor.ts` (may need team header propagation) |
| **Definition of Done** | Three endpoints implemented and secured: `GET /api/teams/my-teams`, `POST /api/teams/active/{teamId}`, `GET /api/teams/active`. Active team stored in HTTP session. Endpoints return ApiResponse-wrapped responses matching frontend model. |
| **Test to Add** | Controller test: mock TeamService, verify endpoint routing and response shape |
| **Depends On** | T8 |

---

### T10: Backend — Scope job/run queries by team

| Field | Value |
|-------|-------|
| **Files Touched** | `JobDefinitionRepository.java`, `JobRunRepository.java`, `JobController.java` (or service layer), security filter configuration |
| **Definition of Done** | All job list/detail queries filter by the active team from session. Existing jobs with null team_id (before backfill runs) are treated as belonging to any team (backward compat during migration window). After backfill, all jobs have a team_id and filtering is strict. ADMIN role bypasses team filter (sees all teams' jobs). |
| **Test to Add** | Integration test: create jobs in two teams, verify each team's list endpoint returns only their jobs |
| **Depends On** | T8, T9 |

---

### T11: Make StepPaletteComponent API-driven

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.ts`, `step-palette.html` |
| **Definition of Done** | Hardcoded `STEP_TYPE_META` removed. Display name comes from API's `schema.displayName`. Icon uses a heuristic function (maps common keywords to Material icons, defaults to `play_arrow`). Field count badge shown per step type. Empty state with retry button when no schemas returned. |
| **Test to Add** | Unit test: verify icon heuristic returns default for unknown types, empty state renders on empty array |
| **Depends On** | Nothing (existing component) |

---

### T12: Update StepFormDialog to use hardened DynamicStepFormComponent

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/features/jobs/step-builder/step-form-dialog.ts`, `step-form-dialog.html` |
| **Definition of Done** | 1. On step type change, trigger form rebuild via DynamicStepFormComponent's schema input change (handled by T6's ngOnChanges). 2. Fetch credentials from CredentialService and pass to dynamic form for SECRET_REF fields. 3. Use `validate()` before submit, show snackbar on validation failure. |
| **Test to Add** | Unit test: verify credentials fetched and passed to child form, validate() gates submission |
| **Depends On** | T6 |

---

### T13: Implement dark mode toggle + CSS variable overrides

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/styles.scss`, new file `orchestrator-ui/src/app/shared/components/theme-toggle/theme-toggle.{ts,html}`, `orchestrator-ui/src/app/app.ts` |
| **Definition of Done** | 1. Dark CSS variable values defined in `[data-theme="dark"]` selector block — overrides all surface colors, text colors, border colors from the existing design tokens. 2. Theme toggle button in toolbar (light_mode / dark_mode icon). 3. Preference persisted in `localStorage('theme')`. 4. Respects `prefers-color-scheme: dark` system preference on first visit (before user has set a preference). 5. Angular Material M3 dark theme applied via `@include mat.theme()` with `color:` map including dark mode tokens. |
| **Test to Add** | Visual verification in dev server. Unit test: verify localStorage read/write, system preference detection |
| **Depends On** | Nothing (CSS-only + small component) |

---

### T14: Build RunTimelineComponent

| Field | Value |
|-------|-------|
| **Files Touched** | New files `orchestrator-ui/src/app/shared/components/run-timeline/run-timeline.{ts,html,scss}` |
| **Definition of Done** | Horizontal timeline renders step bars with correct position/width based on startedAt/endedAt timestamps. Color-coded by status using the existing accent palette CSS variables. Time axis with tick marks. Tooltip on hover showing step name + duration. Handles edge cases: zero-duration steps (minimum bar width), overlapping steps, single-step runs. |
| **Test to Add** | Unit test: verify stepBar calculations for known timestamps, minimum width for zero-duration, color mapping matches status |
| **Depends On** | Nothing (pure presentation component) |

---

### T15: Integrate RunTimelineComponent into RunDetailComponent

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/features/runs/run-detail/run-detail.component.ts`, `run-detail.component.html` |
| **Definition of Done** | Timeline rendered as a section above the step table (or as a tab if tabs are used). Passes `run` data as input. Responsive: collapses gracefully on narrow viewports (reduces height, shows fewer time ticks). |
| **Test to Add** | Visual verification in dev server with different viewport sizes |
| **Depends On** | T14 |

---

### T16: Add DAG canvas stub route

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-ui/src/app/app.routes.ts`, new files `orchestrator-ui/src/app/features/jobs/dag-canvas-stub/dag-canvas-stub.{ts,html}` |
| **Definition of Done** | Route `/jobs/:id/canvas` loads DagCanvasStubComponent. Component shows "Coming Soon" message with Phase 3 reference. TODO comment in code pointing to phase3 plan file. |
| **Test to Add** | Visual verification: navigate to route, confirm placeholder renders |
| **Depends On** | Nothing |

---

### T17: Write unit tests for dynamic form components

| Field | Value |
|-------|-------|
| **Files Touched** | New files `dynamic-field.spec.ts`, `dynamic-step-form.spec.ts`, `step-palette.spec.ts` |
| **Definition of Done** | Each component has ≥5 test cases covering: happy path rendering, validation error display, edge case (empty schema, null config), type-specific rendering. Vitest + jsdom configured and passing. |
| **Test to Add** | The tests themselves are the deliverable |
| **Depends On** | T5, T6, T11 (tests follow implementation) |

---

### T18: Regression smoke test — existing job creation/editing flows

| Field | Value |
|-------|-------|
| **Files Touched** | No new files — manual verification checklist documented in [phase2-05-testing-plan.md](phase2-05-testing-plan.md) |
| **Definition of Done** | All existing flows verified working: create job → add step via palette → configure step via dynamic form → save → run job → view run detail. Dark mode doesn't break any component rendering. Team switcher doesn't interfere with unauthenticated pages (login). |
| **Test to Add** | Checklist in testing plan document |
| **Depends On** | All other tasks |

---

## Dependency Graph

```
T1 ──→ T2 ──→ T3 ──→ T4          (team frontend chain)
                          ↓
T7 ──→ T8 ──→ T9 ──→ T10         (team backend chain)
                          ↓
                    (converges at T4 integration)

T5 ──→ T6 ──→ T12                (form hardening chain)
                  ↓
              T17 (tests)

T11 ────────────┘                (palette, feeds into form dialog)

T13                              (dark mode — independent)
T14 ──→ T15                     (timeline chain)
T16                              (dag stub — independent)
T18 ← all                       (regression — last)
```

**Parallelizable work:** The team backend chain (T7-T10), form hardening chain (T5-T12), dark mode (T13), timeline (T14-T15), and DAG stub (T16) can proceed in parallel across developers. Only T4 and T18 require convergence.
