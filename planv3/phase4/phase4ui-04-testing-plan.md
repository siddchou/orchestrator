<!-- FILE: phase4ui-04-testing-plan.md -->
# Phase 4 UI — Testing Plan

## Unit Tests

### 1. `file-utils.spec.ts` — downloadFile() utility

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| Creates Blob with correct MIME type | Call `downloadFile('content', 'test.json', 'application/json')` | Verify `Blob` constructor called with `['content']` and `{ type: 'application/json' }` | Spy on `new Blob` via module-level override |
| Sets download filename | Same as above | Verify created `<a>` element has `download === 'test.json'` | Check `a.download` property |
| Triggers click and cleans up DOM | Same as above | Verify `a.click()` called, `document.body.appendChild` then `removeChild`, `URL.revokeObjectURL` called | Spy on DOM methods |

### 2. `json-diff.service.spec.ts` — JsonDiffService

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| Identical JSON returns no changes | `compare('{"a":1}', '{"a":1}')` | Result has `hasChanges: false`, `html` is empty string | |
| Added key detected | `compare('{"a":1}', '{"a":1,"b":2}')` | Result has `hasChanges: true`, HTML contains `'b'` and addition styling (green class) | |
| Removed key detected | `compare('{"a":1,"b":2}', '{"a":1}')` | Result has `hasChanges: true`, HTML contains removal styling (red class) | |
| Changed value detected | `compare('{"a":1}', '{"a":99}')` | Result shows both old and new values in HTML | |
| Nested object diff | `compare('{"cfg":{"timeout":5}}', '{"cfg":{"timeout":10}}')` | Result shows change at nested path | |
| Empty objects | `compare('{}', '{}')` | `hasChanges: false` | |
| One empty, one populated | `compare('{}', '{"a":1}')` | `hasChanges: true`, HTML contains addition marker | |

### 3. `job.service.spec.ts` — New methods only

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| exportJob sends correct URL + format param | Mock HttpClient, call `exportJob(42, 'yaml')` | HTTP GET to `/api/jobs/42/export?format=yaml` | Verify `url`, `params` on mock |
| importJob sends POST with body | Mock HttpClient, call `importJob({...})` | HTTP POST to `/api/jobs/import` with request body | Verify method, URL, body |
| listVersions sends correct GET | Call `listVersions(42)` | HTTP GET to `/api/jobs/42/versions` | |
| getVersionSnapshot includes version number | Call `getVersionSnapshot(42, 3)` | HTTP GET to `/api/jobs/42/versions/3` | |
| rollbackToVersion sends POST | Call `rollbackToVersion(42, 2)` | HTTP POST to `/api/jobs/42/versions/2/rollback` | |

### 4. `import-dialog.component.spec.ts` — ImportDialogComponent

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| Initial state shows file selection step | Create component | `component.step === 'select'` | File drop zone visible, preview hidden |
| File selection advances to preview | Simulate file input change with mock JSON blob | FileReader reads content, parses job data | `step === 'preview'`, `parsedJob.jobName` populated |
| Conflict mode shown when job exists | Set `conflictExists = true` in test | Template renders `<mat-radio-group>` | Radio buttons visible for ERROR/UPDATE/SKIP |
| Validation errors block import | Set `validationErrors = ['error1']` | Import button has `[disabled]` attribute | Button disabled, error list visible |
| Malformed JSON shows parse error | Simulate file with invalid JSON content | FileReader callback catches parse error | `validationErrors` contains parse error message |
| Successful import closes dialog | Mock JobService.importJob to return success, click Import | Service called, dialogRef.close() invoked | Dialog closed with result containing job data |
| File reset on init | Create component twice (simulating reopen) | File input value cleared | No stale file from previous session |

### 5. `version-history.component.spec.ts` — VersionHistoryComponent

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| Loads versions on init | Mock listVersions to return 3 versions | Component calls service on ngOnInit | `versions.length === 3`, rendered in template |
| Empty state when no versions | Mock listVersions to return `[]` | Empty state message visible | "No version history yet" text present |
| Compare button disabled without both selections | Set only `diffFromVersion` | Compare button `[disabled]` is true | Button not clickable |
| Same version selection disables compare | Set `diffFromVersion === diffToVersion` | Compare button disabled | Edge case from scenario 8 |
| Diff fetch calls getVersionSnapshot twice | Mock forkJoin with two snapshots, click Compare | Two HTTP GETs to versions endpoints | JsonDiffService.compare called with both snapshot strings |
| No-diff message for identical snapshots | Mock JsonDiffService to return `{ hasChanges: false }` | "No differences" message visible | Not an empty panel |
| Rollback emits versionLoaded event | Mock rollbackToVersion success, confirm dialog | `versionLoaded.emit()` called | Parent can reload job data |
| Current version rollback button disabled | Mark highest version number as current | Rollback button on that row has `[disabled]` | Prevents rolling back to current state |

### 6. `rollback-confirm-dialog.component.spec.ts` — RollbackConfirmDialogComponent

| Test Case | Arrange | Act | Assert |
|-----------|---------|-----|--------|
| Renders version details from data | Pass `RollbackConfirmData` via MAT_DIALOG_DATA | Template shows version number, label, author, date | All fields visible in fixture |
| Warning banner when active run | Set `hasActiveRun: true` in data | Warning div with warning icon visible | Yellow/red warning styling applied |
| Confirm closes dialog with true | Click confirm button | `dialogRef.close(true)` called | Spy on dialogRef.close |
| Cancel closes dialog with false | Click cancel button | `dialogRef.close(false)` called | |

---

## E2E Tests

### E2E Test 1: Export → Import Round Trip

**File:** `orchestrator-ui/src/app/e2e/job-export-import.spec.ts` (or integrate into existing Cypress/Playwright suite — **[ASSUMED]** the project uses Playwright or similar; if no E2E framework exists, use browser-based integration tests via Angular's test harness)

**Steps:**

1. **Setup:** Create a job via API with known data:
   - Job name: "E2E Export Test"
   - Description: "Round trip test job"
   - Working dir: "/tmp/e2e-test"
   - 2 steps (SHELL_EXEC, JAVA_EXEC)
   - 1 env var (TEST_VAR=test_value)

2. **Export:** Navigate to `/jobs/{jobId}`, click "Export JSON" button
   - Assert: Browser download triggered (intercept network request to verify `GET /api/jobs/{id}/export?format=json`)
   - Capture the response body from the intercepted request

3. **Verify export content:** Parse the exported JSON
   - Assert: Contains job name, description, working dir, 2 steps, 1 env var
   - Assert: Step types match (SHELL_EXEC, JAVA_EXEC)

4. **Delete original job** (to test clean import)
   - Click delete button in job list, confirm dialog

5. **Import:** Navigate to job list, click "Import Job"
   - Simulate file upload with the exported JSON content
   - Assert: Preview shows correct job name, step count, env var count
   - Assert: No conflict mode shown (job was deleted)
   - Click "Import Job" button

6. **Verify imported job:** Navigate to imported job detail page
   - Assert: Job name matches original
   - Assert: Step count is 2, types match
   - Assert: Env var count is 1, key/value match
   - Assert: Working dir matches

7. **Cleanup:** Delete the imported test job

**Expected duration:** ~15 seconds

---

### E2E Test 2: Edit → Version History → Diff → Rollback

**Steps:**

1. **Setup:** Create a job with description "Original Description"
   - Navigate to `/jobs/{jobId}`

2. **Edit and save:** Change description to "Modified Description", click "Save Changes"
   - Assert: Success snackbar appears
   - Assert: Page reloads with updated description

3. **View version history:** Click "Versions" tab
   - Assert: At least 2 versions visible (v1 = original, v2 = modified)
   - Assert: Most recent version shows current user as author

4. **Compare versions:** Select "From: v1", "To: v2", click "Compare"
   - Assert: Diff panel appears below version list
   - Assert: Diff shows description field changed from "Original Description" to "Modified Description" (green addition, red removal)

5. **Rollback:** Click "Rollback" on v1 row
   - Assert: Rollback confirmation dialog opens showing v1 details
   - Click confirm button in dialog

6. **Verify rollback:** Versions tab shows a new version (v3 = rollback snapshot)
   - Navigate to "General" tab
   - Assert: Description is reverted to "Original Description"
   - Navigate back to "Versions" tab
   - Assert: Version list now has 3 entries (original, modified, rollback-created)

7. **Diff after rollback:** Compare v2 → v3
   - Assert: Diff shows description changed from "Modified Description" back to "Original Description"

8. **Cleanup:** Delete the test job

**Expected duration:** ~20 seconds

---

## Test Execution Strategy

### Unit Tests
- Run via `ng test` (Vitest, per package.json)
- Target: 80%+ line coverage on new files
- All new component spec files should pass before PR merge

### E2E Tests
- **[ASSUMED]** The project has an E2E test runner configured. If not, these specs serve as manual test checklists until the framework is set up.
- Run against a test database (H2 in-memory or dedicated test schema) to avoid polluting dev data
- Each E2E test should be idempotent: create its own data, clean up after itself

### Integration Test (Backend + UI contract)
The existing `JobExportImportRoundTripTest.java` (found by Graphify) covers the backend round trip. The UI E2E tests above complement this by verifying the user-facing flow, not just the API contract.
