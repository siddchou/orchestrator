<!-- FILE: phase4ui-00-overview.md -->
# Phase 4 UI — Overview

## Scope

Add export (download JSON/YAML) and import (file upload with conflict-mode selection) controls to the job list/detail views, and a "Versions" tab on the job detail page showing version history with a diff view between any two versions and a rollback action.

**What this phase does NOT touch:**
- Job editor/canvas components from Phase 2/3 (StepFormDialog, DagCanvasComponent, StepPalette)
- Backend endpoints — these are assumed implemented by Phase 4 backend work
- CLI tooling — that is Phase 7

## Scope Breakdown

### Feature A: Export Job Definition
- **Location:** Job detail page header + job list actions column
- **Behavior:** Format picker (JSON / YAML), calls `GET /api/jobs/{id}/export?format=...`, triggers browser download of the response string as a file
- **Formats:** JSON (default), YAML

### Feature B: Import Job Definition
- **Location:** Job list page header ("Import Job" button)
- **Behavior:** Opens import dialog → file picker accepts `.json` / `.yaml` files → parses content → displays job name from parsed data → presents conflict-mode radio group if a job with that name exists → validates against backend → on success, navigates to imported job detail

### Feature C: Version History Tab
- **Location:** New 5th tab ("Versions") in `JobDetailComponent`'s `<mat-tab-group>`
- **Behavior:** Loads version list via `GET /api/jobs/{id}/versions`, displays as a timeline-style list showing version number, change note/label, timestamp, and author

### Feature D: Version Diff View
- **Location:** Within the Versions tab
- **Behavior:** User selects two versions → fetches both snapshots via `GET /api/jobs/{id}/versions/{versionNumber}` → renders structured JSON diff using `jsondiffpatch` with inline (unified) format

### Feature E: Rollback to Version
- **Location:** Action button per version row in Versions tab
- **Behavior:** Opens confirmation dialog showing the target version details → on confirm, calls `POST /api/jobs/{id}/versions/{versionNumber}/rollback` → reloads job data → all tabs reflect rolled-back state

## Assumptions ([ASSUMED] — not verified at time of planning)

1. **[ASSUMED]** The backend saves a version snapshot automatically on every `PUT /api/jobs/{id}` (manual edit). If not, the UI must trigger version creation explicitly — but the plan doc says "every import or manual edit writes a new version row," implying a backend hook.
2. **[ASSUMED]** The `GET /api/jobs/{id}/versions/{versionNumber}` endpoint returns the same format as export (full job definition JSON string), making it directly consumable by the diff viewer. Confirmed by controller line 291: `versionService.exportVersion()`.
3. **[ASSUMED]** The backend's `JobImportRequest.mode` enum values (`ERROR`, `UPDATE`, `SKIP`) map to UI-friendly labels: "Fail if exists", "Overwrite existing job", "Skip import". The plan doc mentions `create|overwrite|new_version` — there is a discrepancy between the DTO enum and the plan text. **The code takes precedence:** use ERROR/UPDATE/SKIP with appropriate UI labels.
4. **[ASSUMED]** Rollback while a run is in progress is allowed by the backend but may cause unexpected behavior. The UI should warn if the job has active runs (requires checking run status, which may need a new API call or reusing existing run query).
5. **[ASSUMED]** `jsondiffpatch` v3.x with its built-in CSS plugin will be used. It produces HTML output that can be styled to match Material Design.

## Dependencies

- **New npm package:** `jsondiffpatch` + `jsondiffpatch-formatters` (for HTML rendering)
- **Backend dependency:** All 5 endpoints must be functional before UI integration testing
- **UI component dependency:** Reuses existing `ConfirmDialog`, `MatSnackBar`, `MatDialog`, `JobService`

## Effort Estimate

| Task | Complexity | Story Points | Notes |
|------|-----------|--------------|-------|
| Export button + download utility | Low | 2 | Reuse existing Blob download pattern |
| Import dialog with file upload | Medium | 5 | New file-upload pattern, conflict-mode UI, validation display |
| Versions tab component | Medium | 3 | New tab, new service methods, list rendering |
| Version diff viewer | Medium | 5 | New library integration, snapshot comparison, HTML diff styling |
| Rollback with confirmation | Low | 2 | Dialog + API call + reload |
| Job list export button | Low | 1 | Add icon button to actions column |
| Unit tests (all new components) | Medium | 5 | Mock services, test file parsing, diff rendering |
| E2E: Export → Import round trip | Medium | 3 | Full flow through UI |
| E2E: Edit → Version history → Diff → Rollback | Medium | 3 | Multi-step verification |
| **Total** | | **~28-35 SP** | Allows for integration friction |

## Table of Contents

1. [Code Review Findings](phase4ui-code-review-findings.md) — backend endpoints, existing patterns to reuse
2. [Component Design](phase4ui-01-component-design.md) — detailed design per component
3. [Task Breakdown](phase4ui-02-task-breakdown.md) — PR-sized tasks with DoD
4. [Edge Cases & Failure Modes](phase4ui-03-edge-cases-and-failure-modes.md) — scenarios and required handling
5. [Testing Plan](phase4ui-04-testing-plan.md) — unit + E2E test specifications
