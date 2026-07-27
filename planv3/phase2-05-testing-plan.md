# Phase 2 — Testing Plan

## Unit Tests by Component

### DynamicFieldComponent (hardened)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Renders text input for STRING type | `field={type: STRING, ...}` | `<input type="text">` in DOM |
| Renders number input for NUMBER type | `field={type: NUMBER, ...}` | `<input type="number">` with `step="any"` |
| Renders checkbox for BOOLEAN type | `field={type: BOOLEAN, ...}` | `<mat-checkbox>` in DOM |
| Renders select for ENUM type | `field={type: ENUM, enumValues: ['A','B']}` | `<select>` with 2 options |
| Renders mat-select for SECRET_REF with credentials | `field={type: SECRET_REF}, credentials=[{id:1,name:'k'}]` | `<mat-select>` with one option |
| Renders text input fallback for SECRET_REF without credentials | `field={type: SECRET_REF}, credentials=[]` | `<input type="text">` (no dropdown) |
| Renders chip input for LIST_STRING | `field={type: LIST_STRING}` | Chip list + input visible |
| Shows error when showError=true and control invalid | `showError=true`, control touched+invalid | `<mat-error>` element visible with text |
| Hides error when showError=false | `showError=false`, same invalid state | No `<mat-error>` in DOM |
| Shows warning for unknown FieldType | `field={type: 'UNKNOWN_TYPE'}` | Warning banner text contains field type name |
| Required indicator shows asterisk | `field.required=true` | Red asterisk or `aria-required="true"` present |

**File:** `orchestrator-ui/src/app/shared/components/dynamic-field/dynamic-field.spec.ts`

---

### DynamicStepFormComponent (hardened)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Builds form from schema with 3 fields | `schema={fields: [STRING, NUMBER, BOOLEAN]}` | FormGroup with 3 controls |
| Sets initial values from config | `config={name:'val', count:5}`, matching schema | Controls have `'val'` and `5` as values |
| Rebuilds form on schema change (ngOnChanges) | Set schema A, then set schema B with different fields | FormGroup has controls matching schema B only |
| validate() marks all controls touched | Form with 3 untouched controls | After `validate()`, all 3 have `touched=true` |
| validate() returns false if any invalid | Required STRING left empty | `validate()` returns `false` |
| toConfig() exports LIST_STRING as array | Chip input has ['a','b'] | Config value is `['a', 'b']` (not string) |
| toConfig() returns valid=false when form invalid | Invalid form state | `{config: {...}, valid: false}` |
| Passes credentials to child fields | `credentials=[{id:1,name:'x'}]` | Child DynamicFieldComponents receive credentials input |

**File:** `orchestrator-ui/src/app/shared/components/dynamic-step-form/dynamic-step-form.spec.ts`

---

### StepPaletteComponent (API-driven)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Renders step types from API data | `schemas=[{stepType:'A',displayName:'Alpha'}]` | One card with "Alpha" label |
| Shows icon for known step type keywords | `stepType='ShellCommand'` | Material icon rendered (heuristic maps to terminal/command) |
| Falls back to default icon for unknown type | `stepType='CustomXyz'` | Default `play_arrow` icon |
| Shows field count badge | Schema with 5 fields | Badge showing "5" on card |
| Shows empty state when no schemas | `schemas=[]` | "No step types available" message + retry button |
| Retry button re-fetches schemas | Empty state, click retry | Calls `jobService.listStepTypes()` again |

**File:** `orchestrator-ui/src/app/features/jobs/step-builder/step-palette.spec.ts`

---

### TeamSwitcherComponent (new)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Renders team names from service | `teams=[{teamName:'Alpha'},{teamName:'Beta'}]` | Dropdown shows both names |
| Shows active team highlighted | Active team is 'Alpha' | 'Alpha' has selected state styling |
| Selection triggers page reload | User selects 'Beta' | `TeamService.setActiveTeam()` called, then location reloads |
| Shows spinner while loading | Service returns pending observable | Spinner visible, dropdown disabled |
| Shows empty state with no teams | Empty team array | "No teams assigned" message |
| VIEWER role sees read-only display | User role is 'VIEWER' | Dropdown is disabled or hidden, only current team shown |

**File:** `orchestrator-ui/src/app/shared/components/team-switcher/team-switcher.spec.ts`

---

### RunTimelineComponent (new)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Renders step bars with correct position/width | Two steps with known start/end times | Bars positioned proportionally on timeline |
| Zero-duration step gets minimum width | Step where startedAt === endedAt | Bar has `min-width` applied (not zero) |
| Null timestamps handled gracefully | Step with null startedAt or endedAt | Dashed outline bar with "?" label, no JS error |
| Color maps to status correctly | Steps with SUCCESS/FAILED/RUNNING statuses | CSS classes match status colors from design tokens |
| Tooltip shows step name and duration | Hover over any step bar | Tooltip element contains step name + formatted duration |

**File:** `orchestrator-ui/src/app/shared/components/run-timeline/run-timeline.spec.ts`

---

### AuthService (extended with team)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Stores and retrieves activeTeamId | `setActiveTeamId(5)` | `getActiveTeamId()` returns `5` |
| Team ID persists across loadFromStorage | sessionStorage has team_id: 3 | After `loadFromStorage()`, `getActiveTeamId()` is `3` |
| Returns null when no team set | Fresh auth state with no team | `getActiveTeamId()` returns `null` |

**File:** `orchestrator-ui/src/app/core/services/auth.service.spec.ts` (add to existing tests)

---

### TeamService (new)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| listMyTeams returns typed array | Mocked API response with 2 teams | Returns `Observable<TeamSummary[]>` with correct shape |
| setActiveTeam sends correct URL | `setActiveTeam(7)` | POST to `/api/teams/active/7` |

**File:** `orchestrator-ui/src/app/core/services/team.service.spec.ts`

---

## E2E Test — Step Type End-to-End Flow

**Goal:** Prove that a new step type registered in the backend flows through the entire UI pipeline to job execution.

| Step | Action | Assertion |
|------|--------|-----------|
| 1 | Start dev server with test database seeded with known data | App loads at `/` without errors |
| 2 | Login as OPERATOR user | Dashboard shows job list |
| 3 | Click "Create Job" → fill name/description → save | Job created, redirected to job detail |
| 4 | Click "Add Step" → select a step type from palette | Step-form-dialog opens with dynamic fields for that step type |
| 5 | Fill required fields (STRING + NUMBER) → click Save | Step appears in job's step list with config values visible |
| 6 | Add second step of a different type | Both steps render correctly, each with type-appropriate fields |
| 7 | Reorder steps via drag-and-drop | Steps appear in new order after drop |
| 8 | Run the job | Run starts, both steps execute sequentially |
| 9 | View run detail | RunTimelineComponent shows two step bars, statuses are SUCCESS |
| 10 | Toggle dark mode | All text remains readable, no color inversion issues |
| 11 | Switch team (if user has ≥2 teams) | Job list refreshes to show only the new team's jobs |

**Tooling:** Manual execution checklist for Phase 2. If Cypress/Playwright is added in a future phase, this becomes an automated script.

---

## Regression Checklist

Verify these existing flows still work after all Phase 2 changes:

- [ ] Login page loads and accepts valid credentials
- [ ] Login rejects invalid credentials with error message
- [ ] Logout clears session and redirects to login
- [ ] Job list page shows jobs with correct columns (name, type count, status)
- [ ] Create job with no steps → save → appears in list
- [ ] Edit existing job → change name → save → persists
- [ ] Delete job → confirmation dialog → job removed from list
- [ ] Add environment variable to job → save → visible in env vars tab
- [ ] Add credential reference → save → visible in credentials tab
- [ ] Schedule a job (cron expression) → schedule appears in schedule tab
- [ ] Run job manually → run appears in run history
- [ ] View run detail → shows step execution order and statuses
- [ ] Download run logs → file downloads correctly
- [ ] Admin user management page loads (add/edit/delete users)
- [ ] Role-based access: VIEWER cannot create/edit jobs
- [ ] Auth interceptor attaches Bearer token to API requests
- [ ] 401 response redirects to login
- [ ] Route guards prevent unauthenticated access to protected routes

---

## Backend Integration Tests

| Test | Description |
|------|-------------|
| Migration V7 applies cleanly | Run Flyway against fresh Oracle test DB, verify all tables/constraints created |
| Default team seeded | Query TEAM table — one row with name "Default" exists |
| Existing jobs backfilled | All JOB_DEFINITION rows have non-null TEAM_ID pointing to Default team |
| Team-scoped job query | Create jobs in two teams, verify list endpoint returns only active team's jobs |
| ADMIN bypasses team filter | Login as ADMIN, verify list endpoint returns all jobs regardless of team |
| User with no team gets auto-enrolled | Create user, login once, verify USER_TEAM row created for Default team |
| setActiveTeam rejects non-member | POST to set active team the user isn't in → 403 response |

---

## Test Execution Order

1. **First:** Backend migration + integration tests (T7-T10) — database must be ready before frontend testing
2. **Second:** Frontend unit tests (T17) — run after component implementations are complete
3. **Third:** E2E flow test — run after all components integrate
4. **Last:** Regression checklist — verify nothing broke across the entire app
