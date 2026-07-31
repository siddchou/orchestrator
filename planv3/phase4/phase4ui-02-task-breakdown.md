<!-- FILE: phase4ui-02-task-breakdown.md -->
# Phase 4 UI — Task Breakdown

## Task 1: Add `jsondiffpatch` dependency and CSS

**Files Touched:**
- `orchestrator-ui/package.json` (add `jsondiffpatch`, `jsondiffpatch-formatters`)
- `orchestrator-ui/angular.json` (add CSS from node_modules to styles array)
- `orchestrator-ui/src/assets/styles/jsondiffpatch-overrides.scss` (theme overrides for dark mode)

**Definition of Done:**
- `npm install jsondiffpatch jsondiffpatch-formatters` completed
- HTML formatter CSS included in build
- Dark mode color overrides applied (jsondiffpatch defaults to light theme colors)

**Test to Add:** None (dependency-only task)

**Depends On:** Nothing

---

## Task 2: Create `downloadFile()` utility and extract from KeyDialog

**Files Touched:**
- `orchestrator-ui/src/app/core/utils/file-utils.ts` (new — export `downloadFile`)
- `orchestrator-ui/src/app/features/credentials/key-dialog.component.ts` (refactor to use utility)
- `orchestrator-ui/src/app/core/utils/file-utils.spec.ts` (unit test)

**Definition of Done:**
- `downloadFile(content, filename, mimeType)` function created
- KeyDialog refactored to call the shared utility
- Unit test: verify Blob creation, anchor element lifecycle, URL revocation

**Test to Add:** `file-utils.spec.ts` — mock `document.createElement`, assert `a.download`, `a.click()`, `URL.revokeObjectURL` called

**Depends On:** Nothing

---

## Task 3: Add export/import/version methods to JobService + models

**Files Touched:**
- `orchestrator-ui/src/app/core/services/job.service.ts` (add 5 new methods)
- `orchestrator-ui/src/app/core/models/job.model.ts` (add `JobVersionSummary`, `JobImportRequest`)
- `orchestrator-ui/src/app/core/services/job.service.spec.ts` (add tests for new methods)

**Definition of Done:**
- `exportJob(id, format)` — GET with format param, returns `ApiResponse<string>`
- `importJob(requestBody)` — POST to `/api/jobs/import`, returns `ApiResponse<JobDefinition>`
- `listVersions(jobId)` — GET returns `ApiResponse<JobVersionSummary[]>`
- `getVersionSnapshot(jobId, versionNumber)` — GET returns `ApiResponse<string>`
- `rollbackToVersion(jobId, versionNumber)` — POST returns `ApiResponse<JobDefinition>`
- TypeScript interfaces for request/response types compiled without errors

**Test to Add:** Mock HttpClient, verify correct URL + params for each method

**Depends On:** Nothing

---

## Task 4: Create JsonDiffService

**Files Touched:**
- `orchestrator-ui/src/app/core/services/json-diff.service.ts` (new)
- `orchestrator-ui/src/app/core/services/json-diff.service.spec.ts` (unit test)

**Definition of Done:**
- Service provides `compare(jsonA, jsonB)` → `{ html: string; hasChanges: boolean }`
- Handles empty diffs (identical objects return `hasChanges: false`)
- Handles nested object differences correctly
- HTML output includes added/removed/changed markers

**Test to Add:**
- Identical JSON → `hasChanges: false`, empty HTML
- Added key → `hasChanges: true`, HTML contains green addition marker
- Removed key → HTML contains red removal marker
- Changed value → HTML shows old→new transition

**Depends On:** Task 1 (jsondiffpatch installed)

---

## Task 5: Add export buttons to JobDetailComponent header

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.ts` (add `exportJob()` method)
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.html` (add header-right section with export buttons)
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.scss` (header flex layout)

**Definition of Done:**
- Two buttons: "Export JSON" and "Export YAML" in page header right side
- Click triggers API call, response triggers browser download
- Success snackbar shown; error snackbar on failure
- Buttons disabled while loading or when no job loaded

**Test to Add:** Component test — mock JobService.exportJob, verify `downloadFile` called with correct filename and MIME type

**Depends On:** Task 2 (downloadFile utility), Task 3 (exportJob service method)

---

## Task 6: Add export button to JobListComponent actions column

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/job-list/job-list.component.ts` (add `exportJob()` method)
- `orchestrator-ui/src/app/features/jobs/job-list/job-list.component.html` (add export icon button in actions)

**Definition of Done:**
- Export icon button added to each row's action group
- Triggers same download flow as detail page (JSON format by default)
- Tooltip shows "Export {jobName}"

**Test to Add:** Component test — verify click calls JobService.exportJob with correct jobId

**Depends On:** Task 2, Task 3

---

## Task 7: Create ImportDialogComponent

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.ts` (new)
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.html` (new)
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.scss` (new)
- `orchestrator-ui/src/app/features/jobs/job-list/job-list.component.ts` (add "Import Job" button + dialog open method)
- `orchestrator-ui/src/app/features/jobs/job-list/job-list.component.html` (add import button in header)

**Definition of Done:**
- Dialog opens from job list header "Import Job" button
- File drop zone accepts `.json`, `.yaml`, `.yml` files
- FileReader parses content, extracts job metadata for preview display
- If a job with the same name exists (check via parsing `jobId` field or calling a backend pre-check endpoint), show conflict-mode radio group
- Validation errors from backend displayed as red error list before import button
- On successful import, dialog closes, snackbar shown, navigate to imported job detail

**Test to Add:**
- Component test: file selection → FileReader mock → parsed data populated
- Conflict mode rendering when job exists
- Validation error display blocks Import button
- Successful import emits navigation event

**Depends On:** Task 3 (importJob service method)

---

## Task 8: Create VersionHistoryComponent

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.ts` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.html` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.scss` (new)
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.ts` (import component, add to imports[])
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.html` (add 5th tab with `<app-version-history>`)

**Definition of Done:**
- Component loads versions on init via `JobService.listVersions(jobId)`
- Renders version list: version badge, label, timestamp, author per row
- Empty state shown when no versions exist
- Loading spinner while fetching
- Current version (highest number) visually distinguished
- "Compare" controls: two dropdowns + compare button

**Test to Add:**
- Component test: verify list rendering with mock version data
- Empty state visibility when array is empty
- Compare button disabled without both versions selected

**Depends On:** Task 3 (listVersions service method)

---

## Task 9: Implement version diff view within VersionHistoryComponent

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.ts` (add diff logic)
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.html` (diff panel template)
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.scss` (diff panel styles)

**Definition of Done:**
- Selecting two versions and clicking "Compare" fetches both snapshots via `forkJoin`
- Snapshots passed to `JsonDiffService.compare()`
- HTML diff rendered in `[innerHTML]` bound div below version list
- "No differences" message when versions are identical
- Close button to dismiss diff panel
- Diff panel styled with jsondiffpatch CSS + dark mode overrides

**Test to Add:**
- Component test: mock two snapshots, verify JsonDiffService.compare called
- No-diff message shown for identical snapshots
- Error handling: snapshot fetch failure shows snackbar

**Depends On:** Task 4 (JsonDiffService), Task 8 (VersionHistoryComponent)

---

## Task 10: Create RollbackConfirmDialogComponent

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.ts` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.html` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.scss` (new)

**Definition of Done:**
- Dialog shows version number, label, author, timestamp
- Warning banner if job has active run (`hasActiveRun` flag in data)
- Confirm button styled as warn color with undo icon
- Returns `true` on confirm, `false` on cancel via dialogRef.close()

**Test to Add:**
- Component test: verify dialog renders version details from MAT_DIALOG_DATA
- Warning visible when hasActiveRun is true
- Confirm/close returns correct values

**Depends On:** Nothing

---

## Task 11: Wire rollback action in VersionHistoryComponent

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.ts` (add rollback method)
- `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.ts` (listen to versionLoaded event, reload job)

**Definition of Done:**
- "Rollback" button per version row calls dialog
- On confirm, calls `JobService.rollbackToVersion(jobId, versionNumber)`
- On success, emits `versionLoaded` event → parent component reloads full job data
- Success snackbar: "Rolled back {jobName} to v{N}"
- Rollback button disabled for the current (latest) version

**Test to Add:**
- Component test: mock rollback API call, verify event emission on success
- Current version's rollback button is disabled

**Depends On:** Task 8, Task 10

---

## Task 12: Dark mode styling for all new components

**Files Touched:**
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.scss` (dark mode selectors)
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.scss` (dark mode selectors)
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.scss` (dark mode selectors)
- `orchestrator-ui/src/assets/styles/jsondiffpatch-overrides.scss` (jsondiffpatch dark theme)

**Definition of Done:**
- All new components use CSS custom properties that respect light/dark theme
- jsondiffpatch HTML output colors overridden for dark mode (red/green highlights readable on dark background)
- Verified in browser preview with both themes

**Test to Add:** Visual verification only (no automated test for theming)

**Depends On:** Tasks 7, 8, 9, 10

---

## Task 13: Unit tests — comprehensive coverage

**Files Touched:**
- `orchestrator-ui/src/app/core/utils/file-utils.spec.ts`
- `orchestrator-ui/src/app/core/services/json-diff.service.spec.ts`
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.spec.ts` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.spec.ts` (new)
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.spec.ts` (new)
- `orchestrator-ui/src/app/core/services/job.service.spec.ts` (extended)

**Definition of Done:**
- All new components have spec files with at least happy-path tests
- Service methods tested with mock HttpClient
- JsonDiffService tested with representative JSON pairs
- File utility tested with DOM mocks

**Test to Add:** See per-task test sections above

**Depends On:** Tasks 2-11 (all implementation tasks)

---

## Task 14: E2E test — Export → Import round trip

**Files Touched:**
- `orchestrator-ui/src/app/e2e/job-export-import.spec.ts` (new or added to existing e2e suite)

**Definition of Done:**
- Test creates a job via API, exports as JSON, downloads file content
- Imports the same content, verifies imported job has matching name, step count, env var count
- Verifies navigation to imported job detail page after import

**Test to Add:** One E2E spec covering the full round trip

**Depends On:** Tasks 5, 6, 7 (export + import implemented)

---

## Task 15: E2E test — Edit → Version history → Diff → Rollback

**Files Touched:**
- `orchestrator-ui/src/app/e2e/job-version-history.spec.ts` (new or added to existing e2e suite)

**Definition of Done:**
- Test navigates to job detail, edits a field (description), saves
- Switches to Versions tab, verifies new version appears
- Selects two versions for diff, verifies diff panel shows the changed field
- Clicks rollback on older version, confirms dialog
- Verifies description reverted to original value

**Test to Add:** One E2E spec covering edit → version → diff → rollback flow

**Depends On:** Tasks 8, 9, 10, 11 (version history + diff + rollback implemented)

---

## Task Dependency Graph

```
Task 1 (jsondiffpatch dep) ────────────────→ Task 4 (JsonDiffService) ──→ Task 9 (Diff view)
Task 2 (downloadFile util) ──→ Task 5 (Detail export)                     ↗
                              → Task 6 (List export)                       ↗
Task 3 (JobService methods) ──→ Task 7 (Import dialog)                    ↗
                              → Task 8 (VersionHistory) ──→ Task 9 ──→ Task 12 (Dark mode)
                                                    ↓
                                                  Task 10 (Rollback dialog)
                                                    ↓
                                                  Task 11 (Wire rollback)
                                                           ↓
                                                      Task 13 (Unit tests)
Task 5,6,7 ─────────────────────────────────→ Task 14 (E2E: Export/Import)
Task 8,9,10,11 ─────────────────────────────→ Task 15 (E2E: Version/Rollback)
```

## Summary Table

| # | Task | Files | SP | Depends On |
|---|------|-------|----|------------|
| 1 | jsondiffpatch dependency + CSS | 3 | 1 | — |
| 2 | downloadFile() utility | 3 | 2 | — |
| 3 | JobService methods + models | 3 | 3 | — |
| 4 | JsonDiffService | 2 | 2 | 1 |
| 5 | Export buttons in detail header | 3 | 2 | 2, 3 |
| 6 | Export button in list actions | 2 | 1 | 2, 3 |
| 7 | ImportDialogComponent | 5 | 5 | 3 |
| 8 | VersionHistoryComponent | 5 | 3 | 3 |
| 9 | Version diff view | 3 | 5 | 4, 8 |
| 10 | RollbackConfirmDialogComponent | 3 | 2 | — |
| 11 | Wire rollback action | 2 | 2 | 8, 10 |
| 12 | Dark mode styling | 4 | 2 | 7, 8, 9, 10 |
| 13 | Unit tests (all) | 6 | 5 | 2-11 |
| 14 | E2E: Export → Import round trip | 1 | 3 | 5, 6, 7 |
| 15 | E2E: Edit → Version → Diff → Rollback | 1 | 3 | 8, 9, 10, 11 |

**Total: ~36 story points across 15 tasks**
