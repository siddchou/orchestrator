# Phase 2 — Testing Plan

## Existing Test Coverage

8 spec files exist covering the Phase 2 components:

| File | Component | What's Likely Covered |
|------|-----------|---------------------|
| `dynamic-field.spec.ts` | DynamicFieldComponent | Rendering of each FieldType variant, control binding, error display |
| `dynamic-step-form.spec.ts` | DynamicStepFormComponent | FormGroup building from schema, `toConfig()` output, validation |
| `step-palette.spec.ts` | StepPaletteComponent | API call, filtering, selection result |
| `team-switcher.spec.ts` | TeamSwitcherComponent | Team loading, switching, form guard integration, retry logic |
| `run-timeline.spec.ts` | RunTimelineComponent | Bar computation, time axis generation, status colors |
| `auth.service.spec.ts` | AuthService | Login, token storage, user state management, team methods |
| `team.service.spec.ts` | TeamService | API calls for list/set/get active team |
| `app.spec.ts` | App component | Shell rendering, theme toggle, navigation |

## Tests to Add

### T4.1: JobDetailComponent (new)

**Priority:** High — most complex component in the app, no test coverage.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Load job by ID | Route param `id: 1` | Calls `jobService.getJob(1)`, populates form with job data |
| Add step via palette | User clicks "Add Step" → selects type → fills form → submits | Step appears in table, `jobService.addStep()` called with correct payload |
| Edit existing step | User clicks edit on row | Opens StepFormDialog pre-populated with existing config |
| Delete step | User clicks delete on row | Confirm dialog opens → on confirm, `jobService.deleteStep()` called |
| Drag-and-drop reorder | Drag step 1 to position 3 | Step moves in table, `jobService.updateSteps()` called with new order |
| Form guard — unsaved changes | Modify job name, attempt navigation | FormGuard blocks with confirmation dialog |
| Form guard — saved state | Save job, attempt navigation | No guard prompt — navigation proceeds |
| Tab switching | Click between General/Steps/Env Vars/Schedule tabs | Correct tab content renders, no data loss |
| Empty job state | New job with no steps | "No steps" message displayed, Add Step button visible |
| Error on save | `jobService.saveJob()` returns 500 | Error message displayed in UI, form data preserved |

### T4.2: StepFormDialog (new)

**Priority:** Medium — wraps DynamicStepFormComponent with metadata fields.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| New step form | `dialogData: { stepType: 'SHELL' }` | Fetches schema, renders DynamicStepFormComponent with that schema |
| Edit existing step | `dialogData: { stepType: 'SHELL', edit: true, existingConfig: {...} }` | Form pre-populated with existingConfig values |
| Step type not found | API returns 404 for schema | Warning displayed, save disabled (E3/E5) |
| Required field validation | Submit with required field empty | `validate()` called, error messages visible on fields |
| Credential selection | SECRET_REF field → user selects credential | Selected credential ID appears in config output |
| Cancel dialog | User clicks cancel | Dialog closes, no API calls made |

### T4.3: ThemeService (new)

**Priority:** Low — signal-based service, relatively simple logic.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Initial theme — no preference | Fresh browser, no localStorage, no system preference | Defaults to `'light'` |
| Initial theme — system dark | `prefers-color-scheme: dark`, no localStorage | Sets to `'dark'` |
| Initial theme — stored preference | localStorage has `'dark'`, system is light | Uses stored `'dark'` (user choice overrides system) |
| Toggle from light | Theme is `'light'`, call `toggle()` | Theme becomes `'dark'`, `[data-theme="dark"]` set on `<html>` |
| Toggle from dark | Theme is `'dark'`, call `toggle()` | Theme becomes `'light'`, attribute removed or set to `"light"` |
| localStorage persistence | Call `toggle()` | localStorage updated with new value |
| Signal reactivity | Component subscribes to `theme` signal | Signal emits on toggle, component updates |

### T4.4: FormGuardService (new)

**Priority:** Medium — used by TeamSwitcher and potentially route guards.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Clean state — check | Fresh service, no `markDirty()` call | `check()` returns `false` (no guard) |
| Dirty state — check | `markDirty()` called | `check()` returns confirmation message string |
| Mark clean after dirty | `markDirty()` → `markClean()` | `check()` returns `false` again |
| Multiple dirty marks | `markDirty()` called multiple times | Still one dirty state (idempotent) |
| Dirty across components | Component A marks dirty, Component B checks | Guard fires (shared service instance) |

### T4.5: DynamicFieldComponent — additional edge cases

**Priority:** Medium — supplement existing tests with edge cases from E1, E9.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Unknown FieldType | `fieldDef.type = 'UNKNOWN_TYPE'` | Fallback input renders + warning message visible |
| ENUM with null enumValues | `fieldDef.type = 'ENUM', fieldDef.enumValues = null` | No crash. "No options available" or similar message |
| ENUM with empty enumValues | `fieldDef.type = 'ENUM', fieldDef.enumValues = []` | Empty select with disabled state |
| LIST_STRING chip removal | Chips: `[a, b, c]`, remove index 1 | Chips: `[a, c]`, control value updated |
| SECRET_REF with no credentials | `credentials = []` | Select shows "No credentials available" |

### T4.6: RunTimelineComponent — additional edge cases

**Priority:** Low — supplement existing tests.

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Run with zero steps | `run.steps = []` | Empty state message, no crash |
| Step with null endTime (running) | `step.endTime = null` | Bar extends to right edge or shows "running" indicator |
| Zero-duration step | `step.startTime === step.endTime` | Bar renders with minimum width |
| Steps out of order | Steps not sorted by startTime | Bars render in correct temporal order regardless of array order |

---

## E2E Test Plan (T5)

### Tooling Recommendation
**Playwright** — first-class Angular support, fast execution, good CI integration. Alternative: Cypress if already familiar.

### Critical Path Scenarios

| # | Scenario | Steps | Assertion |
|---|----------|-------|-----------|
| E2E-1 | Login and view dashboard | Navigate to `/login`, enter credentials, submit | Redirected to `/dashboard`, user name visible in toolbar |
| E2E-2 | Create a job | Dashboard → "New Job" → fill name/description → Save | Job appears in list, URL shows `/jobs/:id` |
| E2E-3 | Add a step | Job editor → Steps tab → "Add Step" → select type from palette → fill form → Save | Step appears in steps table with correct type and config |
| E2E-4 | Edit a step | Steps table → click edit on row → modify field → Save | Changes persisted, table reflects new values |
| E2E-5 | Reorder steps | Drag step handle to new position | Order persists after page reload |
| E2E-6 | Trigger run and view timeline | Job editor → "Run Now" → navigate to Run History → click run | Run detail shows steps with status badges + timeline visualization |
| E2E-7 | Switch teams | Toolbar team switcher → select different team | Page reloads, job list shows only that team's jobs |
| E2E-8 | Dark mode toggle | Click theme toggle in toolbar | `[data-theme="dark"]` on `<html>`, UI renders with dark colors |
| E2E-9 | Form guard protection | Edit job name → click team switcher link | Confirmation dialog appears, changes not lost until confirmed |
| E2E-10 | Logout and session expiry | Click logout | Redirected to `/login`, sessionStorage cleared |

---

## Regression Checklist

After each Phase 2 change, verify:

- [ ] Login page loads and authentication works
- [ ] Job list displays jobs for active team
- [ ] Job editor opens existing job without errors
- [ ] Step palette shows all registered step types
- [ ] Dynamic form renders correctly for each FieldType
- [ ] Step save persists config JSON correctly
- [ ] Drag-and-drop reordering works in steps table
- [ ] Run detail displays step statuses and timeline
- [ ] Team switcher loads teams and switches correctly
- [ ] Dark mode toggle works and persists preference
- [ ] Form guard prevents accidental data loss
- [ ] Error interceptor displays user-friendly messages on API errors
- [ ] 401 responses redirect to login page

---

## Backend Integration Tests (if server-side scoping needs work)

| Test | Setup | Assertion |
|------|-------|-----------|
| Team-scoped job list | Create jobs in Team A and Team B. Login as user in Team A only. | `GET /api/jobs` returns only Team A jobs |
| Cross-team access denied | User in Team A attempts `GET /api/jobs/{TeamB_job_id}` | Returns 403 |
| Admin sees all teams | Admin user calls `GET /api/jobs` | Returns jobs from all teams (or with team filter param) |
| Credential scoping | Credentials exist in Team A and Team B. User in Team A. | `GET /api/credentials` returns only Team A credentials |
| Team assignment required | User not in any team attempts job creation | Returns 403 or 400 with clear message |
