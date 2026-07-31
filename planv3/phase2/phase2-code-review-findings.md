# Phase 2 — Code Review Findings (Updated)

> Date: 2026-07-30 | Branch: `plan3-phase2-ui`

## Critical Discovery

**Phase 2 features are already implemented in the codebase.** The previous findings file (dated 2026-07-26) captured a pre-implementation snapshot. This updated review reflects the current state where all Phase 2 components, migrations, and theming exist as working code.

This plan is therefore written as a **retrospective audit** — documenting what was built, identifying gaps, and surfacing remaining polish items rather than prescribing new implementation.

---

## 1. Phase 1 SPI Contract — CONFIRMED EXISTENT

| Artifact | Location | Status |
|----------|----------|--------|
| `GET /api/step-types` | `src/main/java/com/novakai/orchestrator/api/controller/StepTypeController.java:19` | Implemented. Returns `List<StepConfigSchema>` from registry |
| `StepConfigSchema` record | `src/main/java/com/novakai/orchestrator/engine/spi/StepConfigSchema.java` | `(stepType, displayName, fields: List<FieldDefinition>)` |
| `FieldDefinition` record | `src/main/java/com/novakai/orchestrator/engine/spi/FieldDefinition.java` | `(name, label, type, required, defaultValue, enumValues, helpText)` with validation constructor |
| `FieldType` enum | `src/main/java/com/novakai/orchestrator/engine/spi/FieldType.java` | `STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING` — 7 types |

**Frontend mirror models** at [`orchestrator-ui/src/app/core/models/job.model.ts`](orchestrator-ui/src/app/core/models/job.model.ts):
- `FieldType` type alias matches backend enum exactly
- `FieldDefinition` interface mirrors the Java record
- `StepConfigSchema` interface mirrors the Java record

---

## 2. Current Angular App Structure

**Framework:** Angular 21.2.18, standalone components (no NgModules).

```
src/app/
├── app.ts, app.html, app.routes.ts, app.config.ts   # shell + theme toggle + team switcher
├── core/                                             # services, models, guards, interceptors
│   ├── guards/auth.guard.ts
│   ├── interceptors/auth.interceptor.ts, error.interceptor.ts
│   ├── models/job.model.ts, run.model.ts, credential.model.ts, system.model.ts, api-response.model.ts
│   └── services/auth.service.ts, job.service.ts, run.service.ts,
│       credential.service.ts, system.service.ts, log-stream.service.ts,
│       team.service.ts, theme.service.ts, form-guard.service.ts
├── features/                                         # page-level feature areas
│   ├── config/global-config/
│   ├── credentials/
│   ├── dashboard/
│   ├── jobs/
│   │   ├── job-list/
│   │   ├── job-detail/                               # job editor — tabs: General, Steps, Env Vars, Schedule
│   │   ├── step-builder/                             # step-form-dialog.ts, step-palette.ts
│   │   └── dag-canvas-stub/                          # stub component for DAG view
│   ├── login/
│   └── runs/
│       ├── run-list/
│       ├── run-detail/                               # steps table + log viewer + run timeline
│       └── log-viewer/
├── shared/                                           # reusable components
│   ├── components/
│   │   ├── confirm-dialog/
│   │   ├── dynamic-field/                            # renders all 7 FieldType variants
│   │   ├── dynamic-step-form/                        # builds FormGroup from StepConfigSchema
│   │   ├── run-job-dialog/
│   │   ├── run-timeline/                             # horizontal bar chart for step timing
│   │   ├── status-badge/
│   │   └── team-switcher/                            # team selection with form guard + retry
│   └── pipes/duration.pipe.ts
```

---

## 3. Existing Dynamic Components (already implemented)

### DynamicFieldComponent (`shared/components/dynamic-field/dynamic-field.ts`)
- **Inputs:** `fieldDef: FieldDefinition`, `control: FormControl`
- **Renders all 7 FieldType values:** STRING, FILE_PATTERN, LIST_STRING (chip input), NUMBER, BOOLEAN, ENUM, SECRET_REF
- **Has fallback** `*ngSwitchDefault` for unknown types
- **Chip input** for LIST_STRING with Enter-to-add, Backspace-to-remove
- **Credential dropdown** for SECRET_REF fields
- **`KNOWN_TYPES` set** for type validation

### DynamicStepFormComponent (`shared/components/dynamic-step-form/dynamic-step-form.ts`)
- **Inputs:** `schema: StepConfigSchema`, `existingConfig: Record<string, unknown> | null`, `credentials: Credential[]`
- **Builds reactive FormGroup** from schema fields with validators
- **`toConfig()`** converts form values back to config JSON (handles LIST_STRING → array conversion)
- **Resolves initial values** from existingConfig or schema defaults
- **Credential validation** for SECRET_REF fields (E6 edge case)
- **`ngOnChanges`** handles schema/config/credential updates

### StepPaletteComponent (`features/jobs/step-builder/step-palette.ts`)
- Opens as a dialog, fetches step types via `JobService.listStepTypes()`
- Has text filter and hardcoded icon/description metadata per step type
- Returns selected `stepType` string on close

### StepFormDialog (`features/jobs/step-builder/step-form-dialog.ts`)
- Wraps DynamicStepFormComponent with step name, type selector, continueOnFailure, enabled toggles
- Uses `@ViewChild(DynamicStepFormComponent)` to extract config on submit
- Has `isStepTypeRemoved` getter (E5 edge case handling)

### TeamSwitcherComponent (`shared/components/team-switcher/team-switcher.ts`)
- Loads teams from API with retry logic (E12)
- sessionStorage cache fallback
- Form guard integration for unsaved changes (E2)
- Triggers page reload on team switch
- Integrated in `app.ts` root component

### RunTimelineComponent (`shared/components/run-timeline/run-timeline.ts`)
- Takes `@Input() run`, computes bar positions from step start/end times
- Generates time axis ticks
- Color-codes bars by status
- Horizontal bar chart layout

---

## 4. Angular / Material / CDK Versions

| Package | Version | Notes |
|---------|---------|-------|
| Angular Core | 21.2.18 | Latest stable, standalone-first |
| @angular/material | 21.2.14 | M3 theming |
| @angular/cdk | 21.2.14 | `DragDropModule` used in job-detail |
| TypeScript | 5.9.3 | |
| RxJS | 7.8.2 | |
| Vitest | 4.1.10 | Test runner (not Jasmine/Karma) |
| jsdom | 24.1.3 | Test environment |

---

## 5. Authentication / JWT Setup

**Auth service** ([`core/services/auth.service.ts`](orchestrator-ui/src/app/core/services/auth.service.ts)):
- Login via `POST /api/auth/login`, receives `{accessToken, expiresInSeconds, role, passwordExpired}`
- Token stored in **sessionStorage** (key: `orch_auth`) — not localStorage
- `BehaviorSubject<AuthUser | null>` for reactive current user state
- `AuthUser` interface includes `teams?: TeamSummary[]`, `activeTeamId?: number`
- Methods: `getTeams()`, `getActiveTeamId()`, `setActiveTeamId()`, `loadTeams()`

**HTTP interceptor** ([`core/interceptors/auth.interceptor.ts`](orchestrator-ui/src/app/core/interceptors/auth.interceptor.ts)):
- Functional interceptor, attaches `Authorization: Bearer {token}` header

**Auth guard** ([`core/guards/auth.guard.ts`](orchestrator-ui/src/app/core/guards/auth.guard.ts)):
- `authGuard` — checks `isLoggedIn()`, redirects to `/login`
- `adminGuard` — checks `isRole('ADMIN')`

**Roles:** `ADMIN`, `OPERATOR`, `VIEWER` (defined in `APP_USER.ROLE` check constraint)

---

## 6. JOB_DEFINITION Schema & Multi-Tenancy

**Multi-tenancy migration V7 is COMPLETE.** See [`src/main/resources/db/migration/V7__add_multi_tenancy.sql`](src/main/resources/db/migration/V7__add_multi_tenancy.sql):
- Creates `TEAM` table (id, name, description, created_at, updated_at)
- Creates `USER_TEAM` join table with role check constraint (`ADMIN`, `MEMBER`, `VIEWER`)
- Adds nullable `TEAM_ID` FK to `JOB_DEFINITION`
- Seeds "Default" team
- Backfills existing jobs and users to Default team
- Makes `TEAM_ID` NOT NULL after backfill

**Frontend support:**
- **TeamService** ([`core/services/team.service.ts`](orchestrator-ui/src/app/core/services/team.service.ts)) — `listMyTeams()`, `setActiveTeam(teamId)`, `getActiveTeam()` calling `/api/teams/my-teams`, `/api/teams/active/{id}`, `/api/teams/active`
- **TeamSwitcherComponent** — UI for team selection

### Full Migration History

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP, JOB_ENV_VAR |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP |
| V3 | `V3__create_schedule_and_credential.sql` | JOB_SCHEDULE, JOB_CREDENTIAL, AUDIT_LOG |
| V4 | `V4__create_app_user.sql` | APP_USER + seed data |
| V5 | `V5__add_env_setup_to_job_definition.sql` | JAVA_HOME, CLASSPATH columns |
| V6 | `V6__relax_step_type_constraint.sql` | Removes step_type check constraint |
| V7 | `V7__add_multi_tenancy.sql` | TEAM, USER_TEAM, JOB_DEFINITION.TEAM_ID |
| V8 | `V8__add_step_dependencies.sql` | Step dependency columns for DAG support |
| V9 | `V9__backfill_step_dependencies.sql` | Backfills step dependencies |
| V10 | `V10__add_job_definition_version.sql` | Job definition versioning |

**Next free version: V11**

---

## 7. Dark Mode / Theming Infrastructure — IMPLEMENTED

**ThemeService** ([`core/services/theme.service.ts`](orchestrator-ui/src/app/core/services/theme.service.ts)):
- Uses Angular **signals** (`theme` signal with `'light' | 'dark'`)
- `effect()` to sync `data-theme` attribute on `<html>` and localStorage
- Respects system preference (`prefers-color-scheme`) on init
- `toggle()` method for UI

**App component** ([`app.ts`](orchestrator-ui/src/app/app.ts)):
- Injects ThemeService
- Exposes `currentTheme` getter and `toggleTheme()` method
- Theme toggle button rendered in toolbar (line 102-108)

**CSS overrides** ([`styles.scss:559-673`](orchestrator-ui/src/styles.scss:559)):
- `[data-theme="dark"]` selector with `color-scheme: dark`
- Overrides all CSS custom properties for colors, surfaces, accents
- Material component overrides (toolbar, sidenav, table, cards)
- Status badge colors, gradient adjustments
- Log viewer dark background

---

## 8. DAG Canvas Stub — IMPLEMENTED

**Component:** [`features/jobs/dag-canvas-stub/dag-canvas-stub.component.ts`](orchestrator-ui/src/app/features/jobs/dag-canvas-stub/dag-canvas-stub.component.ts)
- Standalone component, imports CommonModule/MatCardModule/MatIconModule
- Minimal placeholder UI

**Route:** [`app.routes.ts:28`](orchestrator-ui/src/app/app.routes.ts) — `/jobs/:id/canvas` → `DagCanvasStubComponent`

---

## 9. Test Coverage — PARTIAL

**Spec files found (8):**

| File | Component Covered |
|------|-------------------|
| `app.spec.ts` | App component |
| `core/services/auth.service.spec.ts` | AuthService |
| `core/services/team.service.spec.ts` | TeamService |
| `shared/components/dynamic-field/dynamic-field.spec.ts` | DynamicFieldComponent |
| `shared/components/dynamic-step-form/dynamic-step-form.spec.ts` | DynamicStepFormComponent |
| `shared/components/run-timeline/run-timeline.spec.ts` | RunTimelineComponent |
| `shared/components/team-switcher/team-switcher.spec.ts` | TeamSwitcherComponent |
| `features/jobs/step-builder/step-palette.spec.ts` | StepPaletteComponent |

**Missing test coverage:**
- `JobDetailComponent` — the main job editor (complex, high value)
- `StepFormDialog` — dialog wrapper for step forms
- `ThemeService` — signal-based theme management
- `FormGuardService` — unsaved changes detection
- `RunDetailComponent` — run history detail view
- Service layer: `JobService`, `RunService`, `CredentialService`, `SystemService`
- E2E tests — none exist

---

## 10. [NOT FOUND] Items

| Item | Search Performed | Result |
|------|-----------------|--------|
| i18n / localization setup | Checked angular.json, app.config.ts | **Not found** — no i18n configured |
| E2E tests | Glob for `*.e2e.ts` | **No E2E test files found** |
| Per-step-type hardcoded forms | Searched for `sftp-step-form`, `archive-step-form` patterns | **Do not exist** — dynamic forms were built first |

---

## 11. Key Implications for Phase 2 Planning

1. **All Phase 2 components are implemented.** The plan should focus on: gap analysis, edge case hardening, test coverage gaps, and polish items.
2. **Step palette uses hardcoded metadata.** Should be driven entirely by the API response's `displayName` field, with icons falling back to a default.
3. **Dark mode is complete** — toggle, CSS overrides, system preference detection, localStorage persistence all work.
4. **Multi-tenancy DB migration V7 is done** — TEAM/USER_TEAM tables exist, backfill is clean. Server-side team scoping via JWT likely needs verification.
5. **Test coverage exists for Phase 2 components** but lacks JobDetailComponent, StepFormDialog, ThemeService, FormGuardService, and E2E tests.
6. **DAG canvas stub route exists** at `/jobs/:id/canvas` — deferred to Phase 2b per constraints.
