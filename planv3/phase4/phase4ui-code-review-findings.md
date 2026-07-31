<!-- FILE: phase4ui-code-review-findings.md -->
# Phase 4 UI — Code Review Findings

## 1. Backend Endpoints (Confirmed)

All endpoints are implemented in `JobDefinitionController.java` (`src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java`).

| Endpoint | Method | Request | Response | Notes |
|----------|--------|---------|----------|-------|
| `/api/jobs/{id}/export?format=json\|yaml` | GET | `format` query param (default: `json`) | `ApiResponse<String>` — serialized JSON or YAML string | Lines 121-131 |
| `/api/jobs/import` | POST | `JobImportRequest` body | `ApiResponse<JobDefinitionResponse>` (201 Created) | Lines 133-166. Validates step types against registry before import. Pre-saves version snapshot on UPDATE mode. |
| `/api/jobs/{id}/versions` | GET | — | `ApiResponse<List<JobVersionSummary>>` | Lines 278-287. Returns versionNumber, versionLabel, changedAt, changedBy |
| `/api/jobs/{id}/versions/{versionNumber}` | GET | — | `ApiResponse<String>` — JSON snapshot of that version | Lines 289-291 |
| `/api/jobs/{id}/versions/{versionNumber}/rollback` | POST | — | `ApiResponse<JobDefinitionResponse>` | Lines 294-304. Requires authenticated user for `createdBy` tracking |

### Import Request Shape (`JobImportRequest.java`)

```java
record JobImportRequest(
    String formatVersion,
    @NotBlank String mode,          // "ERROR" | "UPDATE" | "SKIP"
    String jobId,                   // original ID (informational — not reused)
    String jobName,
    String description,
    String workingDir,
    String javaHome,
    List<String> classpathEntries,
    Boolean enabled,
    String teamName,
    List<ImportStepDefinition> steps,
    List<ImportDependencyDefinition> dependencies,
    List<ImportEnvVarDefinition> envVars,
    ImportScheduleDefinition schedule,
    Map<String, Object> metadata
)
```

**Backend validation:** The controller calls `exportImportService.validateImport(request, exists)` before executing. This validates step types against `StepExecutorRegistry` and credential references. Errors returned as `IllegalArgumentException` with semicolon-delimited messages.

### Version Summary Shape (`JobVersionSummary.java`)

```java
record JobVersionSummary(
    int versionNumber,
    String versionLabel,       // e.g., "username (import pre-update)" or change note
    LocalDateTime changedAt,
    String changedBy           // username
) {}
```

## 2. Job Detail Page — Current Tab Structure

**Component:** `JobDetailComponent` at `orchestrator-ui/src/app/features/jobs/job-detail/`

**Template:** `job-detail.component.html` uses `<mat-tab-group>` with 4 tabs:

| Tab | Label | Content |
|-----|-------|---------|
| 1 | "General" | FormGroup (jobName, description, workingDir, javaHome, classpathEntries) + Save button |
| 2 | "Steps (N)" | Step list (draggable rows) or DAG canvas view, with Add/Edit/Delete step actions |
| 3 | "Environment" | Key-value table for env vars, inline add row |
| 4 | "Schedule" | Cron expression input, validation, enable/disable/delete schedule |

**Page header:** Contains back button, page title ("Edit: {jobName}"), and job ID subtitle. No action buttons on the right side currently — a good place for export/import controls.

**Job list page:** `orchestrator-ui/src/app/features/jobs/job-list/` has an actions column with Edit, Run, Toggle Enable, Delete icon buttons. Export could be added here as well.

## 3. JSON-Diff Library Presence

**NOT FOUND in package.json.** The following were searched and absent:
- `jsondiffpatch` — not present (recommended by plan doc)
- `diff` — not present
- Any other diff-related library — none found

**Decision:** Install `jsondiffpatch` (the plan document explicitly recommends it). It supports structured JSON diff with HTML rendering, which is ideal for side-by-side comparison of job definition snapshots.

## 4. Existing File-Download Pattern

**Found in:** `orchestrator-ui/src/app/features/credentials/key-dialog.component.ts:188-198`

```typescript
downloadPrivateKey() {
    const blob = new Blob([this.data.privateKey], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ssh-private-key-${new Date().toISOString().split('T')[0]}.pem`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
```

**Reuse strategy:** Extract this into a shared utility function (e.g., `downloadFile(content: string, filename: string, mimeType: string)`) in `orchestrator-ui/src/app/core/utils/file-utils.ts` and use it for both export formats.

## 5. Existing File-Upload Pattern

**NOT FOUND IN REPO.** No `<input type="file">`, `FileReader`, or `FormData` usage found in the UI codebase (grep confirmed only false-positive matches on "interface" substring). The import feature will need to create a file-upload pattern from scratch.

**Approach:** Use a standard `<input type="file" accept=".json,.yaml,.yml">` with a `FileReader.readAsText()` callback, parse the result, and send via HttpClient POST. Wrap in a Material dialog for consistency with existing UI patterns.

## 6. Existing Confirm Dialog Pattern

**Component:** `ConfirmDialog` at `orchestrator-ui/src/app/shared/components/confirm-dialog/confirm-dialog.ts`

```typescript
export interface ConfirmData {
  title: string;
  message: string;
  confirmButton?: string;   // defaults to "Confirm" in template
}
```

Used throughout the app for destructive actions (delete step, delete schedule). The rollback action should use a similar dialog but with richer content (showing version details), so a dedicated `RollbackConfirmDialogComponent` is warranted.

## 7. Phase 4 Plan Context

From `planv3/planv3-detailed-implementation-plan.md:205-221`:

> **UI:** version history tab on `/jobs/:id` with a "View diff" (simple JSON diff, e.g. `jsondiffpatch` on the frontend) and "Rollback" button.
> Every import or manual edit writes a new version row (snapshot-based).

## [NOT FOUND] Items

| Item | What Was Tried | Resolution |
|------|---------------|------------|
| Existing file-upload component | Grep for `FileReader`, `FormData`, `input type="file"` across UI src | None exists — create from scratch |
| JSON-diff library dependency | Read package.json, grep for `jsondiffpatch`, `diff` | Not installed — add as new dependency |
| Existing export button on job list | Read job-list.component.html actions column | No export action exists — will be added |
