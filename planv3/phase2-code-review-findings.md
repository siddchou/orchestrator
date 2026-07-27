# Phase 2 — Code Review Findings

> Date: 2026-07-26 | Branch: `phase3` (note: branch name says phase3 but work is phase 2)

## 1. Phase 1 SPI Contract — CONFIRMED EXISTENT

| Artifact | Location | Status |
|----------|----------|--------|
| `GET /api/step-types` | `src/main/java/com/novakai/orchestrator/api/controller/StepTypeController.java:19` | Implemented. Returns `List<StepConfigSchema>` from registry |
| `StepConfigSchema` record | `src/main/java/com/novakai/orchestrator/engine/spi/StepConfigSchema.java` | `(stepType, displayName, fields: List<FieldDefinition>)` |
| `FieldDefinition` record | `src/main/java/com/novakai/orchestrator/engine/spi/FieldDefinition.java` | `(name, label, type, required, defaultValue, enumValues, helpText)` with validation constructor |
| `FieldType` enum | `src/main/java/com/novakai/orchestrator/engine/spi/FieldType.java` | `STRING, NUMBER, BOOLEAN, ENUM, SECRET_REF, FILE_PATTERN, LIST_STRING` — 7 types |

**Frontend mirror models** exist at `orchestrator-ui/src/app/core/models/job.model.ts:1-21`:
- `FieldType` type alias matches backend enum exactly
- `FieldDefinition` interface mirrors the Java record
- `StepConfigSchema` interface mirrors the Java record

**Contradiction with input plan:** The input plan (section 2.1) says "Replace the current per-type hardcoded form components." **No per-type hardcoded forms exist.** The codebase already uses a dynamic form approach. This means Phase 2's form work is about **hardening and extending**, not replacing.

## 2. Current Angular App Structure

**Framework:** Angular 21.2.18, standalone components (no NgModules), `src/app/` structure:

```
src/app/
├── app.ts, app.routes.ts, app.config.ts          # shell
├── core/                                          # services, models, guards, interceptors
│   ├── guards/auth.guard.ts
│   ├── interceptors/auth.interceptor.ts, error.interceptor.ts
│   ├── models/ job.model.ts, run.model.ts, credential.model.ts, system.model.ts, api-response.model.ts
│   └── services/ auth.service.ts, job.service.ts, run.service.ts, credential.service.ts, system.service.ts, log-stream.service.ts
├── features/                                      # page-level feature areas
│   ├── config/global-config/
│   ├── credentials/
│   ├── dashboard/
│   ├── jobs/
│   │   ├── job-list/
│   │   ├── job-detail/                            # the job editor — tabs for General, Steps, Env Vars, Schedule
│   │   └── step-builder/                          # step-form-dialog.ts, step-palette.ts
│   ├── login/
│   └── runs/
│       ├── run-list/
│       ├── run-detail/                            # shows steps as a table with status badges + log viewer
│       └── log-viewer/
└── shared/                                        # reusable components
    ├── components/
    │   ├── confirm-dialog/
    │   ├── dynamic-field/                         # ALREADY EXISTS — renders all 7 FieldType variants
    │   ├── dynamic-step-form/                     # ALREADY EXISTS — builds FormGroup from StepConfigSchema
    │   ├── run-job-dialog/
    │   └── status-badge/
    └── pipes/duration.pipe.ts
```

### Existing Dynamic Components (already implemented)

**`DynamicFieldComponent`** (`shared/components/dynamic-field/dynamic-field.ts`)
- Inputs: `fieldDef: FieldDefinition`, `control: FormControl`
- Template switches on all 7 `FieldType` values: STRING, FILE_PATTERN, LIST_STRING (chip input), NUMBER, BOOLEAN, ENUM, SECRET_REF
- Has a fallback `*ngSwitchDefault` for unknown types
- Chip input for LIST_STRING with Enter-to-add, Backspace-to-remove

**`DynamicStepFormComponent`** (`shared/components/dynamic-step-form/dynamic-step-form.ts`)
- Inputs: `schema: StepConfigSchema`, `existingConfig: Record<string, unknown> | null`
- Builds a reactive `FormGroup` from schema fields with validators
- `toConfig()` method converts form values back to config JSON (handles LIST_STRING → array conversion)
- Resolves initial values from existingConfig or schema defaults

**`StepPaletteComponent`** (`features/jobs/step-builder/step-palette.ts`)
- Opens as a dialog, fetches step types via `JobService.listStepTypes()`
- Has text filter and hardcoded icon/description metadata per step type
- Returns selected `stepType` string on close

**`StepFormDialog`** (`features/jobs/step-builder/step-form-dialog.ts`)
- Wraps DynamicStepFormComponent with step name, type selector, continueOnFailure, enabled toggles
- Uses `@ViewChild(DynamicStepFormComponent)` to extract config on submit

## 3. Job Editor / Step List

**`JobDetailComponent`** (`features/jobs/job-detail/job-detail.component.ts`)
- Single-page editor with tabs: General | Steps | Env Vars | Schedule
- Steps displayed as `MatTable` with columns: stepOrder, stepName, stepType, continueOnFailure, enabled, actions
- **Drag-and-drop reordering** via Angular CDK (`CdkDrag`, `CdkDropList`, `CdkDragHandle`, `moveItemInArray`)
- Add step flow: opens `StepPaletteComponent` dialog → selects type → opens `StepFormDialog`
- Edit step flow: directly opens `StepFormDialog` with existing data
- All CRUD goes through `JobService` → reloads job on success

## 4. Angular / Material / CDK Versions

| Package | Version | Notes |
|---------|---------|-------|
| Angular Core | 21.2.18 | Latest stable, standalone-first |
| @angular/material | 21.2.14 | M3 theming |
| @angular/cdk | 21.2.14 | `DragDropModule` already used in job-detail |
| TypeScript | 5.9.3 | |
| RxJS | 7.8.2 | |
| Vitest | 4.1.10 | Test runner (not Jasmine/Karma) |
| jsdom | 24.1.3 | Test environment |

## 5. Authentication / JWT Setup

**Auth service** (`core/services/auth.service.ts`):
- Login via `POST /api/auth/login`, receives `{accessToken, expiresInSeconds, role, passwordExpired}`
- Token stored in **sessionStorage** (key: `orch_auth`) — not localStorage
- `BehaviorSubject<AuthUser | null>` for reactive current user state
- Exposes: `getToken()`, `getUserRole()`, `isRole(role)`, `isLoggedIn()`

**HTTP interceptor** (`core/interceptors/auth.interceptor.ts`):
- Functional interceptor, attaches `Authorization: Bearer {token}` header

**Auth guard** (`core/guards/auth.guard.ts`):
- `authGuard` — checks `isLoggedIn()`, redirects to `/login`
- `adminGuard` — checks `isRole('ADMIN')`

**Roles:** `ADMIN`, `OPERATOR`, `VIEWER` (defined in `APP_USER.ROLE` check constraint)

**[NOT FOUND]** No JWT claim for team/tenant context. Role is the only user attribute beyond username.

## 6. JOB_DEFINITION Schema & Multi-Tenancy

**Current tables** (from migrations V1-V6):
- `JOB_DEFINITION` — no team/org/tenant column
- `JOB_STEP` — FK to JOB_DEFINITION, step_type check constraint removed in V6
- `JOB_ENV_VAR` — per-job and global env vars
- `JOB_RUN`, `JOB_RUN_STEP` — execution tracking
- `JOB_SCHEDULE`, `JOB_CREDENTIAL` — scheduling and secrets
- `AUDIT_LOG` — audit trail with username but no team context
- `APP_USER` — users with role, no team association

**Confirmed:** No team/org/tenant concept exists anywhere in the schema. The next Flyway version is **V7**.

## 7. Dark Mode / Theming Infrastructure

**Current state** (`src/styles.scss`):
- Material M3 theme using `mat.$azure-palette`, `mat.$cyan-palette`, `mat.$blue-palette`
- Extensive CSS custom properties for spacing, colors, shadows, typography, radius
- `color-scheme: light` hardcoded on `<body>`
- **No dark mode toggle exists** — no `@media (prefers-color-scheme: dark)`, no theme switching logic
- Has design tokens that could support dark mode (all surface colors are CSS variables)
- Log viewer has its own dark background (`--log-bg: #0f121a`)

## 8. Flyway Migration Versions

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP, JOB_ENV_VAR |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP |
| V3 | `V3__create_schedule_and_credential.sql` | JOB_SCHEDULE, JOB_CREDENTIAL, AUDIT_LOG |
| V4 | `V4__create_app_user.sql` | APP_USER + seed data |
| V5 | `V5__add_env_setup_to_job_definition.sql` | JAVA_HOME, CLASSPATH columns |
| V6 | `V6__relax_step_type_constraint.sql` | Removes step_type check constraint |

**Next free version: V7**

## 9. [NOT FOUND] Items

| Item | Search Performed | Result |
|------|-----------------|--------|
| E2E tests | Glob for `*.e2e.ts`, `*.spec.ts` in orchestrator-ui | **No test files found** — the Angular app has zero tests (unit or E2E) |
| Per-step-type hardcoded forms | Searched for `sftp-step-form`, `archive-step-form` patterns | **Do not exist** — dynamic forms were built first |
| Team/org/tenant in backend entities | Grep across all Java source and SQL migrations | **Not found** — single-tenant only |
| i18n / localization setup | Checked angular.json, app.config.ts | **Not found** — no i18n configured |

## 10. Key Implications for Phase 2 Planning

1. **Form components already exist and work.** Phase 2 should focus on: adding validation feedback polish, handling unsupported FieldType gracefully, improving SECRET_REF to be a credential picker (not free-text), and adding unit tests.
2. **Step palette exists but uses hardcoded metadata.** Should be driven entirely by the API response's `displayName` field, with icons falling back to a default.
3. **Multi-tenancy is greenfield.** No existing concept to migrate — clean slate for TEAM/USER_TEAM tables.
4. **Dark mode needs infrastructure.** CSS variables exist but no toggle mechanism or dark palette values. Angular Material M3 supports color scheme swapping via `color-scheme` CSS property.
5. **Run timeline is new.** Run detail shows steps as a table; a horizontal timeline visualization would be an enhancement, not a replacement.
6. **No test infrastructure in Angular.** Vitest is configured but no `.spec.ts` files exist — Phase 2 should establish the testing pattern.
