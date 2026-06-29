# Phase 5b — UI: Dashboard & Job List

> **Goal:** Build the Dashboard (summary cards + recent runs table) and the Job List
> page (searchable, paginated table with per-row actions). These are the two most-used
> views in the application.

> **Depends on:** Phase 5a (models, `JobService`, `RunService`, `AuthService`)  
> **Produces:** `DashboardComponent`, `JobListComponent`, `StatusBadgeComponent`

---

## 5b.1 Shared `StatusBadgeComponent`

Used by both the Dashboard and Run Monitor. Build this first.

```typescript
// shared/components/status-badge/status-badge.component.ts

import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { RunStatus } from '../../../core/models/run.model';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [NgClass],
  template: `
    <span class="badge" [ngClass]="badgeClass">{{ status }}</span>
  `,
  styles: [`
    .badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.5px;
    }
    .badge-success   { background: #e8f5e9; color: #2e7d32; }
    .badge-failed    { background: #ffebee; color: #c62828; }
    .badge-running   { background: #fff8e1; color: #f57f17; }
    .badge-pending   { background: #eeeeee; color: #424242; }
    .badge-partial   { background: #fff3e0; color: #e65100; }
    .badge-cancelled { background: #f3e5f5; color: #6a1b9a; }
  `]
})
export class StatusBadgeComponent {
  @Input() status!: RunStatus;

  get badgeClass(): string {
    const map: Record<RunStatus, string> = {
      SUCCESS:   'badge-success',
      FAILED:    'badge-failed',
      RUNNING:   'badge-running',
      PENDING:   'badge-pending',
      PARTIAL:   'badge-partial',
      CANCELLED: 'badge-cancelled',
    };
    return map[this.status] ?? 'badge-pending';
  }
}
```

---

## 5b.2 `DurationPipe`

```typescript
// shared/pipes/duration.pipe.ts

import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'duration', standalone: true })
export class DurationPipe implements PipeTransform {
  transform(seconds: number): string {
    if (!seconds || seconds < 1) return '< 1s';
    if (seconds < 60) return `${seconds}s`;
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (m < 60) return s > 0 ? `${m}m ${s}s` : `${m}m`;
    const h = Math.floor(m / 60);
    const rm = m % 60;
    return `${h}h ${rm}m`;
  }
}
```

---

## 5b.3 Dashboard Component

Layout: four summary cards at the top, recent runs table below.

```typescript
// features/dashboard/dashboard.component.ts

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatTableModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule,
    StatusBadgeComponent, DurationPipe
  ],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {

  // Summary cards
  totalJobs    = 0;
  runsToday    = 0;
  successRate  = '—';
  runningNow   = 0;

  // Recent runs table
  recentRuns: JobRunSummary[] = [];
  displayedColumns = ['jobName', 'status', 'triggerType', 'startedAt', 'duration', 'actions'];
  loading = true;

  private refreshInterval?: ReturnType<typeof setInterval>;

  constructor(
    private runService: RunService,
    private jobService: JobService,
    private router: Router,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadAll();
    // Refresh every 30 seconds while the dashboard is open
    this.refreshInterval = setInterval(() => this.loadAll(), 30_000);
  }

  loadAll(): void {
    this.loading = true;

    // Recent runs (last 10, newest first)
    this.runService.list({ page: 0, size: 10 }).subscribe({
      next: resp => {
        this.recentRuns = resp.data.content;
        this.runsToday  = resp.data.totalElements;  // approximate
        this.runningNow = this.recentRuns.filter(r => r.status === 'RUNNING').length;
        const done      = this.recentRuns.filter(r =>
          r.status === 'SUCCESS' || r.status === 'FAILED' || r.status === 'PARTIAL');
        const successes = done.filter(r => r.status === 'SUCCESS').length;
        this.successRate = done.length > 0
          ? `${((successes / done.length) * 100).toFixed(1)}%`
          : '—';
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });

    // Total job count
    this.jobService.list(0, 1).subscribe(resp => {
      this.totalJobs = resp.data.totalElements;
    });
  }

  viewRun(runId: number): void {
    this.router.navigate(['/runs', runId]);
  }

  triggerJob(jobId: number): void {
    this.jobService.triggerRun(jobId).subscribe({
      next: () => this.snack.open('Job triggered', 'OK', { duration: 2000 }),
      error: err => this.snack.open(
        err.error?.error ?? 'Failed to trigger job', 'Dismiss', { duration: 4000 })
    });
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }
}
```

```html
<!-- features/dashboard/dashboard.component.html -->

<div class="dashboard-container">

  <!-- Summary Cards -->
  <div class="cards-row">
    <mat-card class="summary-card">
      <mat-card-content>
        <div class="card-value">{{ totalJobs }}</div>
        <div class="card-label">Total Jobs</div>
      </mat-card-content>
    </mat-card>

    <mat-card class="summary-card">
      <mat-card-content>
        <div class="card-value">{{ runsToday }}</div>
        <div class="card-label">Runs Today</div>
      </mat-card-content>
    </mat-card>

    <mat-card class="summary-card">
      <mat-card-content>
        <div class="card-value">{{ successRate }}</div>
        <div class="card-label">Success Rate</div>
      </mat-card-content>
    </mat-card>

    <mat-card class="summary-card highlight-running">
      <mat-card-content>
        <div class="card-value">{{ runningNow }}</div>
        <div class="card-label">Running Now</div>
      </mat-card-content>
    </mat-card>
  </div>

  <!-- Recent Runs Table -->
  <mat-card class="table-card">
    <mat-card-header>
      <mat-card-title>Recent Runs</mat-card-title>
    </mat-card-header>
    <mat-card-content>
      <mat-spinner *ngIf="loading" diameter="32"></mat-spinner>

      <table mat-table [dataSource]="recentRuns" *ngIf="!loading">

        <ng-container matColumnDef="jobName">
          <th mat-header-cell *matHeaderCellDef>Job</th>
          <td mat-cell *matCellDef="let r">{{ r.jobName }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.status" />
          </td>
        </ng-container>

        <ng-container matColumnDef="triggerType">
          <th mat-header-cell *matHeaderCellDef>Trigger</th>
          <td mat-cell *matCellDef="let r">{{ r.triggerType }}</td>
        </ng-container>

        <ng-container matColumnDef="startedAt">
          <th mat-header-cell *matHeaderCellDef>Started</th>
          <td mat-cell *matCellDef="let r">
            {{ r.startedAt | date:'HH:mm:ss' }}
          </td>
        </ng-container>

        <ng-container matColumnDef="duration">
          <th mat-header-cell *matHeaderCellDef>Duration</th>
          <td mat-cell *matCellDef="let r">{{ r.durationSeconds | duration }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-button (click)="viewRun(r.runId)">View</button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"
            [class.running-row]="row.status === 'RUNNING'"></tr>
      </table>
    </mat-card-content>
  </mat-card>

</div>
```

```scss
/* features/dashboard/dashboard.component.scss */
.dashboard-container { padding: 24px; }
.cards-row { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.summary-card { flex: 1; min-width: 160px; }
.card-value { font-size: 36px; font-weight: 700; line-height: 1; }
.card-label { font-size: 13px; color: #666; margin-top: 4px; }
.highlight-running .card-value { color: #f57f17; }
.table-card { overflow: auto; }
table { width: 100%; }
.running-row { background: #fffde7; }
```

---

## 5b.4 Job List Component

```typescript
// features/jobs/job-list/job-list.component.ts

@Component({
  selector: 'app-job-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatTableModule, MatPaginatorModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSlideToggleModule,
    MatTooltipModule, MatSnackBarModule
  ],
  templateUrl: './job-list.component.html'
})
export class JobListComponent implements OnInit {

  jobs: JobDefinition[]  = [];
  totalElements = 0;
  pageSize = 20;
  loading = false;

  searchControl = new FormControl('');
  displayedColumns = ['jobName', 'workingDir', 'enabled', 'steps', 'schedule', 'actions'];

  constructor(
    private jobService: JobService,
    private router: Router,
    private dialog: MatDialog,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadJobs(0);

    // Debounced search
    this.searchControl.valueChanges.pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.loadJobs(0));
  }

  loadJobs(page: number): void {
    this.loading = true;
    this.jobService.list(page, this.pageSize, this.searchControl.value ?? '')
      .subscribe({
        next: resp => {
          this.jobs = resp.data.content;
          this.totalElements = resp.data.totalElements;
          this.loading = false;
        },
        error: () => { this.loading = false; }
      });
  }

  onPage(event: PageEvent): void {
    this.loadJobs(event.pageIndex);
  }

  edit(jobId: number): void {
    this.router.navigate(['/jobs', jobId]);
  }

  create(): void {
    this.router.navigate(['/jobs/new']);
  }

  triggerRun(job: JobDefinition): void {
    this.jobService.triggerRun(job.jobId).subscribe({
      next: () => this.snack.open(`${job.jobName} triggered`, 'OK', { duration: 2000 }),
      error: err => this.snack.open(
        err.error?.error ?? 'Failed to trigger', 'Dismiss', { duration: 4000 })
    });
  }

  toggleEnabled(job: JobDefinition): void {
    this.jobService.toggleEnabled(job.jobId).subscribe({
      next: resp => {
        job.enabled = resp.data.enabled;
      }
    });
  }

  deleteJob(job: JobDefinition): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete "${job.jobName}"? This cannot be undone.` }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.delete(job.jobId).subscribe({
        next: () => {
          this.snack.open('Job deleted', 'OK', { duration: 2000 });
          this.loadJobs(0);
        }
      });
    });
  }
}
```

```html
<!-- features/jobs/job-list/job-list.component.html -->

<div class="list-container">
  <div class="list-header">
    <h2>Jobs</h2>
    <button mat-raised-button color="primary" (click)="create()">
      <mat-icon>add</mat-icon> New Job
    </button>
  </div>

  <mat-form-field appearance="outline" class="search-field">
    <mat-label>Search jobs</mat-label>
    <input matInput [formControl]="searchControl" placeholder="Type job name...">
    <mat-icon matSuffix>search</mat-icon>
  </mat-form-field>

  <mat-spinner *ngIf="loading" diameter="32"></mat-spinner>

  <table mat-table [dataSource]="jobs" *ngIf="!loading">

    <ng-container matColumnDef="jobName">
      <th mat-header-cell *matHeaderCellDef>Job Name</th>
      <td mat-cell *matCellDef="let j">
        <strong>{{ j.jobName }}</strong>
        <div class="desc">{{ j.description }}</div>
      </td>
    </ng-container>

    <ng-container matColumnDef="workingDir">
      <th mat-header-cell *matHeaderCellDef>Working Dir</th>
      <td mat-cell *matCellDef="let j" class="mono">{{ j.workingDir }}</td>
    </ng-container>

    <ng-container matColumnDef="enabled">
      <th mat-header-cell *matHeaderCellDef>Enabled</th>
      <td mat-cell *matCellDef="let j">
        <mat-slide-toggle [checked]="j.enabled"
          (change)="toggleEnabled(j)"></mat-slide-toggle>
      </td>
    </ng-container>

    <ng-container matColumnDef="steps">
      <th mat-header-cell *matHeaderCellDef>Steps</th>
      <td mat-cell *matCellDef="let j">{{ j.steps.length }}</td>
    </ng-container>

    <ng-container matColumnDef="schedule">
      <th mat-header-cell *matHeaderCellDef>Schedule</th>
      <td mat-cell *matCellDef="let j">
        {{ j.schedule ? j.schedule.cronExpression : '—' }}
      </td>
    </ng-container>

    <ng-container matColumnDef="actions">
      <th mat-header-cell *matHeaderCellDef></th>
      <td mat-cell *matCellDef="let j">
        <button mat-icon-button matTooltip="Edit" (click)="edit(j.jobId)">
          <mat-icon>edit</mat-icon>
        </button>
        <button mat-icon-button matTooltip="Run Now" color="primary" (click)="triggerRun(j)">
          <mat-icon>play_arrow</mat-icon>
        </button>
        <button mat-icon-button matTooltip="Delete" color="warn" (click)="deleteJob(j)">
          <mat-icon>delete</mat-icon>
        </button>
      </td>
    </ng-container>

    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
  </table>

  <mat-paginator
    [length]="totalElements"
    [pageSize]="pageSize"
    [pageSizeOptions]="[10, 20, 50]"
    (page)="onPage($event)">
  </mat-paginator>
</div>
```

---

## 5b.5 `ConfirmDialogComponent` (Shared)

```typescript
// shared/components/confirm-dialog/confirm-dialog.component.ts

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Confirm</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-button color="warn" [mat-dialog-close]="true">Confirm</button>
    </mat-dialog-actions>
  `
})
export class ConfirmDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { message: string }
  ) {}
}
```

---

## Phase 5b Acceptance Criteria

- [ ] Dashboard loads and shows 4 summary cards populated from API data
- [ ] Dashboard recent runs table shows last 10 runs with status badges
- [ ] Dashboard auto-refreshes every 30 seconds without manual page reload
- [ ] Job list loads and paginates correctly (20 per page by default)
- [ ] Search debounces 350ms and filters results by job name
- [ ] Enable/disable toggle calls the API and updates the row in place (no full reload)
- [ ] Delete opens confirmation dialog; confirming removes the row and shows a snackbar
- [ ] Run Now button triggers the job and shows a success snackbar
- [ ] `StatusBadgeComponent` renders the correct color for each `RunStatus`
- [ ] `DurationPipe` formats `0`, `45`, `90`, `3720` seconds correctly

---

**Previous:** [Phase 5a — Setup, Models & Services](./PHASE-5a-UI-Setup-Models-Services.md)  
**Next:** [Phase 5c — Job Detail & Step Forms](./PHASE-5c-UI-JobDetail-StepForms.md)
