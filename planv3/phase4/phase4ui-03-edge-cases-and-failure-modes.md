<!-- FILE: phase4ui-03-edge-cases-and-failure-modes.md -->
# Phase 4 UI — Edge Cases & Failure Modes

## Scenario Matrix

| # | Scenario | Current Behavior Without Fix | Required Handling |
|---|----------|------------------------------|-------------------|
| 1 | Import file references a step type not registered on this server | Backend returns `IllegalArgumentException` with semicolon-delimited errors. UI would show a generic "Import failed" snackbar, losing the specific error detail about which step types are unknown. | Parse the backend error message and display each validation error as an individual red error line in the import dialog (see Task 7 template: `validationErrors[]`). Do NOT wrap in a generic "something went wrong" message. The backend format is `"Import validation failed: {err1}; {err2}"` — split on `"; "` after stripping the prefix. |
| 2 | Import of a very large job definition (100+ steps, complex config) causing slow/frozen diff render | `jsondiffpatch` processes the entire delta in the main thread. Large diffs block the UI for seconds, appearing frozen to the user. | (a) Show a loading spinner while computing the diff: wrap `JsonDiffService.compare()` in a `setTimeout(..., 0)` or `requestAnimationFrame` to yield to the event loop before processing. (b) For very large snapshots (>100KB), show a warning: "Large diff — rendering may take a moment." (c) Cap the diff HTML at a reasonable height with scroll overflow. |
| 3 | User attempts rollback while a run of that job is currently in progress | Backend allows the rollback (no guard detected in controller code). The running execution continues using the old step definitions already loaded in memory, but new runs would use the rolled-back definition — creating inconsistency. | (a) Before showing the rollback dialog, check if any run for this job has status `RUNNING` or `PENDING`. This requires a call to the run query service (existing `run.service.ts`). If an active run exists, pass `hasActiveRun: true` to the dialog data. (b) Dialog shows a yellow warning banner: "This job currently has an active run. Rolling back will affect new runs but the current execution continues with the previous definition." (c) Do NOT block rollback — allow it with explicit acknowledgment. |
| 4 | Diff between two versions with no actual differences | `jsondiffpatch` returns `null` delta for identical objects. Without handling, `[innerHTML]="null"` renders nothing, leaving an empty diff panel that looks like a bug. | Check `hasChanges: false` from JsonDiffService and display a friendly message: "No differences between these versions." styled as a neutral info state (not error). See Task 9 template: `<p class="no-diff-message">`. |
| 5 | Export triggered for a job with unsaved local edits in the General form | The export API call hits the backend, which returns the last saved version — not the user's unsaved changes. User may believe their edits are included. | Check `generalForm.dirty` before exporting. If dirty, show a confirmation dialog: "You have unsaved changes. Export will use the last saved version. Save first?" with options: [Save then Export] [Export anyway] [Cancel]. This integrates with the existing `FormGuardService` pattern used for team-switch protection. |
| 6 | Import file is malformed JSON (truncated, syntax error) | `JSON.parse()` throws, uncaught exception crashes the component or shows a blank error. | Wrap FileReader's parse step in try-catch. On parse failure, set `validationErrors = ['Failed to parse file as JSON: ' + error.message]` and display in the dialog. Do not proceed to preview step. |
| 7 | Import file is YAML format but UI only parses JSON | The import dialog calls `JSON.parse()` on YAML content, which fails with a syntax error (same as scenario 6). | Detect YAML by checking if content starts with indicators (`---`, top-level `key: value` patterns) or by file extension. If YAML detected, show message: "YAML import requires JSON format. Please export as JSON before importing." **Alternative:** Send raw YAML to backend and let the backend parse it (the backend already supports YAML via `exportImportService.exportToYaml()` — check if `importJob` also handles YAML). **[ASSUMED]** Backend import accepts both formats; UI sends raw text body. If so, no client-side parsing needed for validation — just send to backend and display errors from response. |
| 8 | User selects the same version in both "From" and "To" diff dropdowns | Diff shows no changes (same as scenario 4), but it's a user error rather than a legitimate comparison. | Disable the "Compare" button when `diffFromVersion === diffToVersion`. Alternatively, show inline validation: "Select different versions to compare." |
| 9 | Version history API returns empty array for a newly created job | Component renders nothing without context. User may think something broke. | Show an empty state with explanatory text: "No version history yet. Versions are created automatically when you save changes or import a new definition." See Task 8 template. |
| 10 | Rollback API call succeeds but the returned JobDefinition is stale (race condition with concurrent edit) | The rolled-back job data is displayed, but a concurrent save may have overwritten it silently. | After rollback success, always reload the full job via `loadJob()` (which calls `GET /api/jobs/{id}`). This ensures the UI shows the authoritative server state. The existing `versionLoaded` event pattern already does this. |
| 11 | Network timeout during export download | API call hangs or returns 504. User sees no feedback beyond a loading spinner (if implemented). | Add a timeout to the HTTP request: `this.http.get(..., { timeout: setTimeout(30_000) })`. On timeout, show error snackbar: "Export timed out — job definition may be too large." |
| 12 | User navigates away from job detail page while diff is computing | The `forkJoin` for snapshot fetch completes after the component is destroyed. RxJS subscription throws or leaks memory. | Use `takeUntilDestroyed()` (Angular 16+) or a manual `Subject` in `ngOnDestroy` to unsubscribe from pending diff requests. The current codebase uses `ngOnDestroy` with `formGuard.markClean()` — follow that pattern. |
| 13 | Import dialog: user selects a file, then closes and reopens dialog | File input retains the previous selection. Selecting the same file again does not fire the `change` event (browser behavior). | Reset the file input value to empty on dialog open: `fileInput.nativeElement.value = ''`. Clear `parsedJob`, `step`, and `validationErrors` in `ngOnInit`. |
| 14 | Export filename contains characters invalid for the OS filesystem | Browser's download mechanism handles most cases, but special characters (`/`, `\`, `:`, `*`) in job names can cause issues. | Sanitize the export filename: replace invalid characters with underscores. `filename = jobName.replace(/[\/\\:*?"<>|]/g, '_')`. |
| 15 | Multiple users editing the same job — version history shows interleaved changes from different authors | This is expected behavior, but without clear author attribution, it's confusing. | Ensure each version row prominently displays `changedBy` (already in the design). Consider adding a relative timestamp ("2 hours ago") alongside the absolute time for quick scanning. |

## Severity Classification

| Severity | Scenarios |
|----------|-----------|
| **Critical** (data loss / corruption risk) | 1, 3, 5, 7, 10 |
| **High** (user confusion / broken UX) | 2, 4, 6, 8, 13 |
| **Medium** (polish / robustness) | 9, 11, 12, 14, 15 |

## Implementation Notes per Scenario

### Scenario 1 — Step type validation errors

The backend controller at `JobDefinitionController.java:153-155` throws:
```java
throw new IllegalArgumentException("Import validation failed: " + String.join("; ", errors));
```

The UI's global error handler (or the import dialog's error callback) should intercept this specific message pattern and extract individual errors for display. Do not genericize — preserve the backend's specific language about which step types are unknown.

### Scenario 3 — Active run detection

This requires checking `run.service.ts` for active runs. The existing service likely has a method to list runs for a job. If not, add:
```typescript
getActiveRuns(jobId: number): Observable<ApiResponse<JobRun[]>> {
  return this.http.get<ApiResponse<JobRun[]>>(`${this.api}/runs?jobId=${jobId}&status=RUNNING`);
}
```

### Scenario 7 — YAML import support

**[ASSUMED]** The backend's `POST /api/jobs/import` accepts the raw export format (JSON or YAML) in the request body. The `JobImportRequest` DTO is a Java record that Jackson deserializes from JSON. If the body is YAML, Jackson needs a YAML module configured. **Verify during implementation:** if the backend only accepts JSON for import, the UI must enforce JSON-only file selection and display a clear message about YAML conversion.
