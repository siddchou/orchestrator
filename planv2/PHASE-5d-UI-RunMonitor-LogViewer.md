# Phase 5d — UI: Run Monitor & Live Log Viewer

> **Goal:** Build the Run List page (filterable, paginated), the Run Detail page
> (step-by-step timeline), and the Live Log Viewer component that streams output
> from the SSE endpoint in real time.

> **Depends on:** Phase 5a (`RunService`, `LogStreamService`, `StatusBadgeComponent`, `DurationPipe`)  
> **Produces:** `RunListComponent`, `RunDetailComponent`, `LogViewerComponent`

---

## 5d.1 Run List Component

```typescript
// features/runs/run-list/run-list.component.ts

@Component({
  selector: 'app-run-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatTableModule, MatPaginatorModule, MatSelectModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatDatepickerModule, MatNativeDateModule,
    StatusBadgeComponent, DurationPipe
  ],
  templateUrl: './run-list.component.html'
})
export class RunListComponent implements OnInit {

  runs: JobRunSummary[] = [];
  totalElements = 0;
  pageSize = 20;
  loading = false;

  displayedColumns = ['jobName', 'status', 'triggerType', 'triggeredBy',
                      'startedAt', 'duration', 'actions'];

  // Filter controls
  filterForm = new FormGroup({
    jobId:  new FormControl<number | null>(null),
    status: new FormControl<RunStatus | null>(null),
    from:   new FormControl<Date | null>(null),
    to:     new FormControl<Date | null>(null),
  });

  statusOptions: RunStatus[] = ['PENDING','RUNNING','SUCCESS','FAILED','PARTIAL','CANCELLED'];
  currentPage = 0;

  constructor(
    private runService: RunService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadRuns(0);
  }

  loadRuns(page: number): void {
    this.currentPage = page;
    this.loading = true;
    const f = this.filterForm.value;

    this.runService.list({
      jobId:  f.jobId  ?? undefined,
      status: f.status ?? undefined,
      from:   f.from   ? this.formatDate(f.from) : undefined,
      to:     f.to     ? this.formatDate(f.to)   : undefined,
      page,
      size: this.pageSize
    }).subscribe({
      next: resp => {
        this.runs = resp.data.content;
        this.totalElements = resp.data.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  applyFilters(): void { this.loadRuns(0); }
  clearFilters(): void {
    this.filterForm.reset();
    this.loadRuns(0);
  }

  onPage(event: PageEvent): void { this.loadRuns(event.pageIndex); }

  viewRun(runId: number): void { this.router.navigate(['/runs', runId]); }

  private formatDate(d: Date): string {
    return d.toISOString().split('T')[0];
  }
}
```

```html
<!-- features/runs/run-list/run-list.component.html -->

<div class="list-container">
  <h2>Run History</h2>

  <!-- Filters -->
  <form [formGroup]="filterForm" class="filter-row">
    <mat-form-field appearance="outline">
      <mat-label>Job ID</mat-label>
      <input matInput type="number" formControlName="jobId" placeholder="All jobs">
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Status</mat-label>
      <mat-select formControlName="status">
        <mat-option [value]="null">All</mat-option>
        <mat-option *ngFor="let s of statusOptions" [value]="s">{{ s }}</mat-option>
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>From</mat-label>
      <input matInput [matDatepicker]="fromPicker" formControlName="from">
      <mat-datepicker-toggle matSuffix [for]="fromPicker"></mat-datepicker-toggle>
      <mat-datepicker #fromPicker></mat-datepicker>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>To</mat-label>
      <input matInput [matDatepicker]="toPicker" formControlName="to">
      <mat-datepicker-toggle matSuffix [for]="toPicker"></mat-datepicker-toggle>
      <mat-datepicker #toPicker></mat-datepicker>
    </mat-form-field>

    <button mat-raised-button color="primary" (click)="applyFilters()">Apply</button>
    <button mat-stroked-button (click)="clearFilters()">Clear</button>
  </form>

  <mat-spinner *ngIf="loading" diameter="32"></mat-spinner>

  <table mat-table [dataSource]="runs" *ngIf="!loading">

    <ng-container matColumnDef="jobName">
      <th mat-header-cell *matHeaderCellDef>Job</th>
      <td mat-cell *matCellDef="let r"><strong>{{ r.jobName }}</strong></td>
    </ng-container>

    <ng-container matColumnDef="status">
      <th mat-header-cell *matHeaderCellDef>Status</th>
      <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
    </ng-container>

    <ng-container matColumnDef="triggerType">
      <th mat-header-cell *matHeaderCellDef>Trigger</th>
      <td mat-cell *matCellDef="let r">{{ r.triggerType }}</td>
    </ng-container>

    <ng-container matColumnDef="triggeredBy">
      <th mat-header-cell *matHeaderCellDef>By</th>
      <td mat-cell *matCellDef="let r">{{ r.triggeredBy }}</td>
    </ng-container>

    <ng-container matColumnDef="startedAt">
      <th mat-header-cell *matHeaderCellDef>Started</th>
      <td mat-cell *matCellDef="let r">{{ r.startedAt | date:'dd MMM HH:mm:ss' }}</td>
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
        [class.running-row]="row.status === 'RUNNING'"
        style="cursor:pointer" (click)="viewRun(row.runId)"></tr>
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

## 5d.2 Run Detail Component

Shows run metadata and a step-by-step timeline. For `RUNNING` jobs the Live Log
Viewer is displayed inline below the timeline.

```typescript
// features/runs/run-detail/run-detail.component.ts

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatSnackBarModule,
    MatDialogModule, StatusBadgeComponent, DurationPipe, LogViewerComponent
  ],
  templateUrl: './run-detail.component.html'
})
export class RunDetailComponent implements OnInit, OnDestroy {

  run?: JobRunDetail;
  loading = true;
  selectedStepId?: number;     // which step's log is open
  stepLog?: string;
  loadingLog = false;

  private pollInterval?: ReturnType<typeof setInterval>;

  constructor(
    private route: ActivatedRoute,
    private runService: RunService,
    private snack: MatSnackBar,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    const runId = +this.route.snapshot.paramMap.get('runId')!;
    this.loadRun(runId);
  }

  loadRun(runId: number): void {
    this.runService.get(runId).subscribe({
      next: resp => {
        this.run = resp.data;
        this.loading = false;

        // Poll every 3s while the run is active
        if (this.run.status === 'RUNNING' || this.run.status === 'PENDING') {
          this.pollInterval = setInterval(() => this.loadRun(runId), 3000);
        } else {
          this.stopPolling();
        }
      },
      error: () => { this.loading = false; }
    });
  }

  viewStepLog(step: RunStepDetail): void {
    this.selectedStepId = step.runStepId;
    this.loadingLog = true;
    this.stepLog = undefined;
    this.runService.getStepLog(this.run!.runId, step.runStepId).subscribe({
      next: resp => {
        this.stepLog = resp.data;
        this.loadingLog = false;
      },
      error: () => { this.loadingLog = false; }
    });
  }

  cancel(): void {
    this.runService.cancel(this.run!.runId).subscribe({
      next: () => this.snack.open('Cancel requested', 'OK', { duration: 2000 }),
      error: () => this.snack.open('Cancel failed', 'Dismiss', { duration: 3000 })
    });
  }

  isActive(): boolean {
    return this.run?.status === 'RUNNING' || this.run?.status === 'PENDING';
  }

  stepIcon(status: RunStatus): string {
    const icons: Record<RunStatus, string> = {
      SUCCESS: 'check_circle', FAILED: 'cancel', RUNNING: 'pending',
      PENDING: 'radio_button_unchecked', PARTIAL: 'warning', CANCELLED: 'block'
    };
    return icons[status] ?? 'help';
  }

  stepIconColor(status: RunStatus): string {
    const colors: Record<RunStatus, string> = {
      SUCCESS: 'green', FAILED: 'red', RUNNING: 'orange',
      PENDING: 'grey', PARTIAL: 'orange', CANCELLED: 'purple'
    };
    return colors[status] ?? 'grey';
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = undefined;
    }
  }

  ngOnDestroy(): void { this.stopPolling(); }
}
```

```html
<!-- features/runs/run-detail/run-detail.component.html -->

<div class="detail-container" *ngIf="!loading && run">

  <!-- Header -->
  <div class="run-header">
    <button mat-icon-button routerLink="/runs"><mat-icon>arrow_back</mat-icon></button>
    <div>
      <h2>Run #{{ run.runId }} — {{ run.jobName }}</h2>
      <div class="run-meta">
        <app-status-badge [status]="run.status" />
        &nbsp;·&nbsp;{{ run.triggerType }}
        &nbsp;·&nbsp;By {{ run.triggeredBy }}
        &nbsp;·&nbsp;{{ run.startedAt | date:'dd MMM yyyy HH:mm:ss' }}
        &nbsp;·&nbsp;{{ run.durationSeconds | duration }}
      </div>
    </div>
    <button mat-stroked-button color="warn" *ngIf="isActive()" (click)="cancel()">
      <mat-icon>stop</mat-icon> Cancel
    </button>
  </div>

  <!-- Step Timeline -->
  <mat-card class="timeline-card">
    <mat-card-header><mat-card-title>Step Timeline</mat-card-title></mat-card-header>
    <mat-card-content>
      <div *ngFor="let step of run.steps" class="step-row"
           [class.selected]="selectedStepId === step.runStepId">
        <mat-icon [style.color]="stepIconColor(step.status)">
          {{ stepIcon(step.status) }}
        </mat-icon>
        <span class="step-order">{{ step.stepOrder }}.</span>
        <span class="step-name">{{ step.stepName }}</span>
        <span class="step-type chip">{{ step.stepType }}</span>
        <span class="step-duration">{{ step.durationSeconds | duration }}</span>
        <app-status-badge [status]="step.status" />
        <span class="spacer"></span>
        <button mat-button (click)="viewStepLog(step)">View Log</button>
      </div>
    </mat-card-content>
  </mat-card>

  <!-- Static Log Panel (completed steps) -->
  <mat-card class="log-card" *ngIf="selectedStepId && !isActive()">
    <mat-card-header>
      <mat-card-title>Step Log</mat-card-title>
    </mat-card-header>
    <mat-card-content>
      <mat-spinner *ngIf="loadingLog" diameter="24"></mat-spinner>
      <pre class="log-pre" *ngIf="stepLog">{{ stepLog }}</pre>
    </mat-card-content>
  </mat-card>

  <!-- Live Log Viewer (active run) -->
  <mat-card class="log-card" *ngIf="isActive()">
    <mat-card-header>
      <mat-card-title>Live Log</mat-card-title>
    </mat-card-header>
    <mat-card-content>
      <app-log-viewer [runId]="run.runId" />
    </mat-card-content>
  </mat-card>

</div>

<mat-spinner *ngIf="loading" diameter="40" style="margin: 40px auto;"></mat-spinner>
```

---

## 5d.3 Live Log Viewer Component

```typescript
// features/runs/log-viewer/log-viewer.component.ts

@Component({
  selector: 'app-log-viewer',
  standalone: true,
  imports: [CommonModule, MatSlideToggleModule, MatButtonModule, FormsModule],
  template: `
    <div class="log-toolbar">
      <mat-slide-toggle [(ngModel)]="autoScroll">Auto-scroll</mat-slide-toggle>
      <button mat-button (click)="clearLog()">Clear</button>
      <span class="log-count">{{ logLines().length }} lines</span>
    </div>
    <div class="log-container" #logContainer>
      <pre class="log-pre">{{ logLines().join('\n') }}</pre>
      <span class="cursor" *ngIf="running">▮</span>
    </div>
  `,
  styles: [`
    .log-toolbar {
      display: flex; align-items: center; gap: 16px;
      padding: 8px 0; border-bottom: 1px solid #e0e0e0; margin-bottom: 8px;
    }
    .log-count { margin-left: auto; font-size: 12px; color: #999; }
    .log-container {
      background: #1e1e1e; color: #d4d4d4;
      font-family: 'Consolas', monospace; font-size: 13px;
      height: 420px; overflow-y: auto;
      padding: 12px 16px; border-radius: 4px;
    }
    .log-pre { margin: 0; white-space: pre-wrap; word-break: break-all; }
    .cursor { animation: blink 1s step-start infinite; }
    @keyframes blink { 50% { opacity: 0; } }
  `]
})
export class LogViewerComponent implements OnInit, OnDestroy {
  @Input() runId!: number;
  @ViewChild('logContainer') logContainer!: ElementRef<HTMLDivElement>;

  logLines  = signal<string[]>([]);
  autoScroll = true;
  running    = true;

  private sub?: Subscription;

  constructor(private logStream: LogStreamService) {}

  ngOnInit(): void {
    this.sub = this.logStream.streamLog(this.runId).subscribe({
      next: line => {
        this.logLines.update(lines => [...lines, line]);
        if (this.autoScroll) this.scrollToBottom();
      },
      complete: () => {
        this.running = false;
        this.logLines.update(lines => [...lines, '', '─── Run complete ───']);
      },
      error: () => {
        this.running = false;
        this.logLines.update(lines => [...lines, '', '─── Stream disconnected ───']);
      }
    });
  }

  clearLog(): void { this.logLines.set([]); }

  private scrollToBottom(): void {
    // Defer until after Angular renders the new line
    setTimeout(() => {
      const el = this.logContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 0);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();   // closes the EventSource via LogStreamService teardown
  }
}
```

---

## Phase 5d Acceptance Criteria

- [ ] Run list loads with correct pagination and "newest first" default order
- [ ] Status filter, job ID filter, and date range filters work independently and together
- [ ] Clicking a row navigates to Run Detail
- [ ] Run Detail shows correct step timeline with icons and colors per status
- [ ] "View Log" on a completed step loads and displays the full log text
- [ ] For a `RUNNING` job, the Live Log Viewer is shown instead of the static panel
- [ ] Live Log Viewer auto-scrolls to the bottom as new lines arrive
- [ ] Auto-scroll toggle stops scrolling; manual scroll up does not jump back down
- [ ] Live Log Viewer shows "Run complete" marker when the SSE `done` event fires
- [ ] `ngOnDestroy` on `LogViewerComponent` closes the `EventSource` — verify in browser Network tab that the connection closes
- [ ] Run Detail polls every 3s while `RUNNING` and stops polling once a terminal status is reached
- [ ] Cancel button appears only for `RUNNING`/`PENDING` runs; calling it shows a snackbar

---

**Previous:** [Phase 5c — Job Detail & Step Forms](./PHASE-5c-UI-JobDetail-StepForms.md)  
**Next:** [Phase 5e — Routing, Global Config & Build Integration](./PHASE-5e-UI-Routing-Config-Build.md)
