<!-- FILE: phase4ui-01-component-design.md -->
# Phase 4 UI — Component Design

## Shared Utility: `downloadFile()`

**File:** `orchestrator-ui/src/app/core/utils/file-utils.ts`

Extracted from the existing pattern in `key-dialog.component.ts`.

```typescript
export function downloadFile(content: string, filename: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
```

---

## Component 1: Export Controls

### Location A — Job Detail Header

**Modified file:** `orchestrator-ui/src/app/features/jobs/job-detail/job-detail.component.html`

The page header currently has a left section (back button + title). Add a right section with export controls.

```html
<div class="page-header">
  <div class="header-left">
    <!-- existing: back button, title, subtitle -->
  </div>
  <div class="header-right">
    <button mat-stroked-button (click)="exportJob('json')">
      <mat-icon>download</mat-icon> Export JSON
    </button>
    <button mat-stroked-button (click)="exportJob('yaml')">
      <mat-icon>download</mat-icon> Export YAML
    </button>
  </div>
</div>
```

### Location B — Job List Actions Column

**Modified file:** `orchestrator-ui/src/app/features/jobs/job-list/job-list.component.html`

Add an export icon button to the actions column, after the delete button:

```html
<button mat-icon-button [routerLink]="'/jobs/' + job.jobId" aria-label="Export {{ job.jobName }}">
  <mat-icon>download</mat-icon>
</button>
<!-- OR: use a dropdown menu for cleaner UX if space is tight -->
```

**Alternative (preferred):** Add a mat-menu to the existing actions, keeping icon count manageable. However, since export is a primary portability action, a dedicated button is justified per the plan scope.

### TypeScript Method (JobDetailComponent)

```typescript
exportJob(format: 'json' | 'yaml'): void {
  if (!this.jobId || !this.job) return;
  this.jobService.exportJob(this.jobId, format).subscribe({
    next: (res) => {
      if (res.status === 'SUCCESS') {
        const ext = format === 'yaml' ? 'yaml' : 'json';
        const mimeType = format === 'yaml' ? 'text/yaml' : 'application/json';
        downloadFile(res.data, `${this.job!.jobName}.${ext}`, mimeType);
        this.snackBar.open(`Exported ${this.job!.jobName} as ${format.toUpperCase()}`, 'Dismiss', { duration: 2000 });
      }
    },
    error: () => {
      this.snackBar.open('Export failed', 'Dismiss', { duration: 3000, panelClass: 'error-snackbar' });
    },
  });
}
```

### JobService Addition

**Modified file:** `orchestrator-ui/src/app/core/services/job.service.ts`

```typescript
exportJob(id: number, format: 'json' | 'yaml'): Observable<ApiResponse<string>> {
  return this.http.get<ApiResponse<string>>(`${this.api}/jobs/${id}/export`, {
    params: { format },
  });
}

importJob(fileContent: string): Observable<ApiResponse<JobDefinition>> {
  // Parse the file content to extract jobName and determine mode
  const parsed = JSON.parse(fileContent);
  return this.http.post<ApiResponse<JobDefinition>>(`${this.api}/jobs/import`, parsed);
}
```

---

## Component 2: Import Dialog

**New files:**
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.ts`
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.html`
- `orchestrator-ui/src/app/features/jobs/job-import/import-dialog.component.scss`

### Dialog Flow

1. **File Selection Phase:** User drops or selects a `.json` / `.yaml` file
2. **Parsing Phase:** File content read via `FileReader.readAsText()`, parsed as JSON (YAML files are expected to be converted by backend; UI sends raw text and lets backend handle format detection via `formatVersion` field)
3. **Preview Phase:** Dialog displays extracted job name, step count, env var count from parsed data
4. **Conflict Resolution Phase:** If backend reports a job with this name exists (pre-check via API or from parsed `jobId` presence), show radio group for mode selection
5. **Import Phase:** Submit to `POST /api/jobs/import`, navigate to imported job on success

### Template Structure

```html
<div class="import-dialog">
  <h3 mat-dialog-title>Import Job Definition</h3>

  <mat-dialog-content>
    <!-- Step 1: File Selection -->
    @if (step === 'select') {
      <div class="file-drop-zone"
           (drop)="onFileDrop($event)"
           (dragover)="onDragOver($event)"
           (click)="fileInput.click()">
        <input #fileInput type="file" accept=".json,.yaml,.yml" (change)="onFileSelected($event)" hidden />
        <mat-icon>cloud_upload</mat-icon>
        <p>Drop a job definition file here, or click to browse</p>
        <span class="hint">.json or .yaml files exported from this system</span>
      </div>
    }

    <!-- Step 2: Preview + Conflict Resolution -->
    @if (step === 'preview' && parsedJob) {
      <div class="job-preview">
        <h4>{{ parsedJob.jobName }}</h4>
        @if (parsedJob.description) {
          <p>{{ parsedJob.description }}</p>
        }
        <div class="preview-stats">
          <span class="stat">{{ parsedJob.steps?.length || 0 }} steps</span>
          <span class="stat">{{ parsedJob.envVars?.length || 0 }} env vars</span>
          @if (parsedJob.schedule) {
            <span class="stat">Scheduled</span>
          }
        </div>

        @if (conflictExists) {
          <mat-divider></mat-divider>
          <h4>A job with this name already exists</h4>
          <mat-radio-group [(ngModel)]="importMode" class="mode-group">
            <mat-radio-button value="UPDATE">Overwrite existing job (saves current version first)</mat-radio-button>
            <mat-radio-button value="ERROR">Fail import — skip this job</mat-radio-button>
            <mat-radio-button value="SKIP">Skip without error</mat-radio-button>
          </mat-radio-group>
        }
      </div>

      @if (validationErrors.length > 0) {
        <div class="validation-errors">
          <mat-icon color="warn">error</mat-icon>
          @for (err of validationErrors; track $index) {
            <p class="error-msg">{{ err }}</p>
          }
        </div>
      }
    }

    <!-- Loading state during import -->
    @if (step === 'importing') {
      <div class="importing-state">
        <mat-spinner diameter="32"></mat-spinner>
        <p>Importing job definition...</p>
      </div>
    }
  </mat-dialog-content>

  <mat-dialog-actions align="end">
    <button mat-stroked-button (click)="cancel()" [disabled]="step === 'importing'">Cancel</button>
    @if (step === 'preview') {
      <button mat-flat-button color="primary"
              (click)="executeImport()"
              [disabled]="validationErrors.length > 0 || step === 'importing'">
        <mat-icon>upload_file</mat-icon> Import Job
      </button>
    }
  </mat-dialog-actions>
</div>
```

### TypeScript Logic

```typescript
export class ImportDialogComponent {
  step: 'select' | 'preview' | 'importing' = 'select';
  parsedJob: any = null;
  conflictExists = false;
  importMode = 'ERROR';
  validationErrors: string[] = [];
  private fileContent = '';

  // FileReader reads the selected file, parses JSON
  // Sends a dry-run validation request to backend (or parses locally for jobName)
  // If jobName matches existing job, sets conflictExists = true
  // Backend does the real validation; UI shows errors from failed pre-check
}
```

### Triggering the Dialog

**Job list component:** Add "Import Job" button in header actions:

```html
<button mat-stroked-button (click)="openImportDialog()">
  <mat-icon>upload_file</mat-icon> Import Job
</button>
```

---

## Component 3: VersionHistoryComponent (Versions Tab)

**New files:**
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.ts`
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.html`
- `orchestrator-ui/src/app/features/jobs/version-history/version-history.component.scss`

### Integration into JobDetailComponent

Add as a 5th tab in the `<mat-tab-group>`:

```html
<!-- Tab 5: Versions -->
@if (job) {
  <app-version-history
    [jobId]="jobId!"
    [jobName]="job.jobName"
    (versionLoaded)="loadJob()"
  ></app-version-history>
}
```

Import `VersionHistoryComponent` in the component's `imports[]` array.

### Component Design

**Inputs:**
- `@Input() jobId: number` — to fetch versions from API
- `@Input() jobName: string` — for display context

**Outputs:**
- `@Output() versionLoaded = new EventEmitter<void>()` — emitted after rollback, triggers parent to reload job data

**Template:**

```html
<div class="version-history">
  @if (loading) {
    <div class="loading"><mat-spinner diameter="24"></mat-spinner></div>
  } @else if (versions.length === 0) {
    <div class="empty-state">
      <mat-icon>history</mat-icon>
      <p>No version history yet. Versions are created on save and import.</p>
    </div>
  } @else {
    <!-- Diff selector -->
    <div class="diff-selector">
      <mat-form-field appearance="outline" class="compact">
        <mat-label>From version</mat-label>
        <mat-select [(ngModel)]="diffFromVersion">
          @for (v of versions; track v.versionNumber) {
            <mat-option [value]="v.versionNumber">{{ 'v' + v.versionNumber }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-icon class="arrow">arrow_right</mat-icon>
      <mat-form-field appearance="outline" class="compact">
        <mat-label>To version</mat-label>
        <mat-select [(ngModel)]="diffToVersion">
          @for (v of versions; track v.versionNumber) {
            <mat-option [value]="v.versionNumber">{{ 'v' + v.versionNumber }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="viewDiff()" [disabled]="!diffFromVersion || !diffToVersion">
        <mat-icon>compare</mat-icon> Compare
      </button>
    </div>

    <!-- Version list -->
    <div class="version-list">
      @for (version of versions; track version.versionNumber) {
        <div class="version-row" [class.current]="isCurrentVersion(version)">
          <div class="version-badge">{{ 'v' + version.versionNumber }}</div>
          <div class="version-info">
            <div class="version-label">{{ version.versionLabel || '(auto)' }}</div>
            <div class="version-meta">
              {{ formatDateTime(version.changedAt) }} by {{ version.changedBy }}
            </div>
          </div>
          <span class="spacer"></span>
          <button mat-stroked-button color="warn"
                  (click)="rollbackToVersion(version)"
                  [disabled]="isCurrentVersion(version)"
                  [attr.title]="'Rollback to ' + 'v' + version.versionNumber">
            <mat-icon>undo</mat-icon> Rollback
          </button>
        </div>
      }
    </div>

    <!-- Diff view (shown when comparing) -->
    @if (showDiff && diffHtml) {
      <div class="diff-panel">
        <div class="diff-header">
          <h4>Diff: v{{ diffFromVersion }} → v{{ diffToVersion }}</h4>
          <button mat-icon-button (click)="closeDiff()">
            <mat-icon>close</mat-icon>
          </button>
        </div>
        @if (noDifferences) {
          <p class="no-diff-message">No differences between these versions.</p>
        } @else {
          <div class="diff-content" [innerHTML]="diffHtml"></div>
        }
      </div>
    }
  }
</div>
```

### Service Methods to Add (JobService)

```typescript
listVersions(jobId: number): Observable<ApiResponse<JobVersionSummary[]>> {
  return this.http.get<ApiResponse<JobVersionSummary[]>>(`${this.api}/jobs/${jobId}/versions`);
}

getVersionSnapshot(jobId: number, versionNumber: number): Observable<ApiResponse<string>> {
  return this.http.get<ApiResponse<string>>(`${this.api}/jobs/${jobId}/versions/${versionNumber}`);
}

rollbackToVersion(jobId: number, versionNumber: number): Observable<ApiResponse<JobDefinition>> {
  return this.http.post<ApiResponse<JobDefinition>>(
    `${this.api}/jobs/${jobId}/versions/${versionNumber}/rollback`, {}
  );
}
```

---

## Component 4: VersionDiffComponent (Inline Diff Renderer)

Rather than a separate component, the diff rendering is embedded within `VersionHistoryComponent` for simplicity — the diff panel appears below the version list when the user clicks "Compare."

### Diff Engine Setup

**File:** `orchestrator-ui/src/app/core/services/json-diff.service.ts`

```typescript
import { Injectable } from '@angular/core';
import { createTwoPatchesInterceptor, diff } from 'jsondiffpatch';
import { formatters } from 'jsondiffpatch-formatters';

@Injectable({ providedIn: 'root' })
export class JsonDiffService {
  private differ = diff;
  private htmlFormatter = formatters.html;

  /**
   * Compare two JSON strings and return HTML diff output.
   * Returns { html: string, hasChanges: boolean }
   */
  compare(jsonA: string, jsonB: string): { html: string; hasChanges: boolean } {
    const objA = JSON.parse(jsonA);
    const objB = JSON.parse(jsonB);
    const delta = this.differ(objA, objB);

    if (!delta) return { html: '', hasChanges: false };

    const html = this.htmlFormatter.convert(delta, 0, {
      unknownBase as any: 'Unknown',       // handle keys only in B
      unknownNew as any: 'Added',          // handle keys only in A
    });
    return { html, hasChanges: true };
  }
}
```

**CSS:** Include `jsondiffpatch` HTML formatter CSS. Copy the minified CSS from `node_modules/jsondiffpatch-formatters/dist/` to `src/assets/styles/jsondiffpatch.css` and include in `angular.json` styles array, or import in component SCSS.

---

## Component 5: RollbackConfirmDialogComponent

**New files:**
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.ts`
- `orchestrator-ui/src/app/features/jobs/version-history/rollback-confirm-dialog.component.html`

Extends the existing `ConfirmDialog` pattern with version-specific context.

### Data Interface

```typescript
export interface RollbackConfirmData {
  jobName: string;
  versionNumber: number;
  versionLabel: string | null;
  changedBy: string;
  changedAt: string;
  hasActiveRun?: boolean;   // if a run is currently in progress
}
```

### Template

```html
<div class="rollback-dialog">
  <div class="dialog-header" [class.warning]="data.hasActiveRun">
    <mat-icon>{{ data.hasActiveRun ? 'warning' : 'undo' }}</mat-icon>
    <h3>Rollback {{ data.jobName }}</h3>
  </div>

  <mat-dialog-content>
    <p>Roll back to <strong>v{{ data.versionNumber }}</strong>?</p>

    <div class="version-details">
      <div class="detail-row">
        <span class="label">Change:</span>
        <span>{{ data.versionLabel || '(auto-saved)' }}</span>
      </div>
      <div class="detail-row">
        <span class="label">By:</span>
        <span>{{ data.changedBy }}</span>
      </div>
      <div class="detail-row">
        <span class="label">Date:</span>
        <span>{{ data.changedAt | date:'medium' }}</span>
      </div>
    </div>

    @if (data.hasActiveRun) {
      <div class="active-run-warning">
        <mat-icon>warning</mat-icon>
        <span>This job currently has an active run. Rolling back may affect the running execution.</span>
      </div>
    }

    <p class="hint">A new version will be created before rollback to preserve the current state.</p>
  </mat-dialog-content>

  <mat-dialog-actions align="end">
    <button mat-stroked-button (click)="cancel()">Cancel</button>
    <button mat-flat-button color="warn" (click)="confirm()" cdkFocusInitial>
      <mat-icon>undo</mat-icon> Rollback to v{{ data.versionNumber }}
    </button>
  </mat-dialog-actions>
</div>
```

---

## Component Interaction Diagram

```
JobListComponent
  ├── [Export icon] → JobService.exportJob() → downloadFile()
  └── [Import button] → MatDialog.open(ImportDialogComponent)
                            ├── FileReader parses file
                            ├── Pre-check: does jobName exist?
                            ├── Show conflict-mode radio if needed
                            └── JobService.importJob() → navigate to /jobs/{newId}

JobDetailComponent (existing, extended)
  ├── [Export JSON/YAML buttons] → JobService.exportJob() → downloadFile()
  └── <app-version-history> (new tab)
        ├── OnInit: JobService.listVersions(jobId)
        ├── [Compare vA → vB] → forkJoin(getVersionSnapshot(A), getVersionSnapshot(B))
        │                         → JsonDiffService.compare() → render HTML diff
        └── [Rollback to vX] → MatDialog.open(RollbackConfirmDialogComponent)
                                └── JobService.rollbackToVersion(jobId, X)
                                    → emit versionLoaded() → parent reloads job
```

## New Model Interfaces

Add to `orchestrator-ui/src/app/core/models/job.model.ts`:

```typescript
export interface JobVersionSummary {
  versionNumber: number;
  versionLabel: string | null;
  changedAt: string;     // ISO datetime from LocalDateTime serialization
  changedBy: string;
}

export interface JobImportRequest {
  formatVersion?: string;
  mode: 'ERROR' | 'UPDATE' | 'SKIP';
  jobId?: string;
  jobName: string;
  description?: string;
  workingDir: string;
  javaHome?: string;
  classpathEntries?: string[];
  enabled?: boolean;
  teamName?: string;
  steps?: any[];
  dependencies?: any[];
  envVars?: any[];
  schedule?: any;
  metadata?: Record<string, unknown>;
}
```
