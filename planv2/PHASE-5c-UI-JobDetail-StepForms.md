# Phase 5c — UI: Job Detail & Dynamic Step Forms

> **Goal:** Build the Job Detail page (4-tab layout) and the dynamic step form dialog
> that renders different fields per `StepType`. This is the most complex UI component.

> **Depends on:** Phase 5a (models, `JobService`), Phase 5b (`ConfirmDialogComponent`)  
> **Produces:** `JobDetailComponent`, `StepFormDialogComponent`, `ScheduleEditorComponent`

---

## 5c.1 Job Detail Component — 4-Tab Layout

The Job Detail page is used for both creating a new job (`/jobs/new`) and editing
an existing one (`/jobs/:id`). It uses Angular's `ActivatedRoute` to distinguish.

```typescript
// features/jobs/job-detail/job-detail.component.ts

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatTabsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSlideToggleModule,
    MatTableModule, MatDialogModule, MatSnackBarModule,
    MatTooltipModule, CdkDrag, CdkDropList, CdkDragHandle,
    StepFormDialogComponent, ScheduleEditorComponent
  ],
  templateUrl: './job-detail.component.html'
})
export class JobDetailComponent implements OnInit {

  isNew = false;
  jobId?: number;
  job?: JobDefinition;

  // Tab 1 — General
  generalForm = new FormGroup({
    jobName:    new FormControl('', [Validators.required, Validators.maxLength(200)]),
    description:new FormControl(''),
    workingDir: new FormControl('', [Validators.required])
  });

  // Tab 2 — Steps (displayed as drag-and-drop list)
  steps: JobStep[] = [];

  // Tab 3 — Env Vars (inline edit table)
  envVars: EnvVar[] = [];
  newVarName  = '';
  newVarValue = '';

  saving = false;
  pathValidation: Record<string, string> = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private jobService: JobService,
    private dialog: MatDialog,
    private snack: MatSnackBar,
    private http: HttpClient   // for path validation call
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id || id === 'new') {
      this.isNew = true;
    } else {
      this.jobId = +id;
      this.loadJob();
    }
  }

  loadJob(): void {
    this.jobService.get(this.jobId!).subscribe(resp => {
      this.job = resp.data;
      this.generalForm.patchValue({
        jobName:     this.job.jobName,
        description: this.job.description ?? '',
        workingDir:  this.job.workingDir
      });
      this.steps   = [...this.job.steps];
      this.envVars = [...this.job.envVars];
    });
  }

  // --- Tab 1: General ---

  saveGeneral(): void {
    if (this.generalForm.invalid) return;
    this.saving = true;
    const body = this.generalForm.value as { jobName: string; description: string; workingDir: string };

    const call = this.isNew
      ? this.jobService.create(body)
      : this.jobService.update(this.jobId!, body);

    call.subscribe({
      next: resp => {
        this.saving = false;
        if (this.isNew) {
          this.isNew = false;
          this.jobId = resp.data.jobId;
          this.router.navigate(['/jobs', this.jobId], { replaceUrl: true });
          this.snack.open('Job created', 'OK', { duration: 2000 });
        } else {
          this.snack.open('Saved', 'OK', { duration: 2000 });
        }
      },
      error: () => { this.saving = false; }
    });
  }

  validatePaths(): void {
    const javaHome   = this.generalForm.value.workingDir ?? '';
    const workingDir = this.generalForm.value.workingDir ?? '';
    this.http.get<ApiResponse<Record<string, string>>>(
      '/api/system/env-validate',
      { params: { javaHome, workingDir } }
    ).subscribe(resp => {
      this.pathValidation = resp.data;
    });
  }

  // --- Tab 2: Steps ---

  openAddStep(): void {
    if (!this.jobId) { this.snack.open('Save the job first', 'OK', { duration: 2000 }); return; }
    const ref = this.dialog.open(StepFormDialogComponent, {
      width: '600px',
      data: { jobId: this.jobId, step: null, nextOrder: this.steps.length + 1 }
    });
    ref.afterClosed().subscribe((saved: JobStep | null) => {
      if (saved) { this.steps = [...this.steps, saved].sort((a, b) => a.stepOrder - b.stepOrder); }
    });
  }

  openEditStep(step: JobStep): void {
    const ref = this.dialog.open(StepFormDialogComponent, {
      width: '600px',
      data: { jobId: this.jobId!, step }
    });
    ref.afterClosed().subscribe((saved: JobStep | null) => {
      if (saved) {
        this.steps = this.steps.map(s => s.stepId === saved.stepId ? saved : s);
      }
    });
  }

  deleteStep(step: JobStep): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete step "${step.stepName}"?` }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.deleteStep(this.jobId!, step.stepId).subscribe(() => {
        this.steps = this.steps.filter(s => s.stepId !== step.stepId);
        // Re-number locally — server re-numbers too
        this.steps.forEach((s, i) => s.stepOrder = i + 1);
        this.snack.open('Step deleted', 'OK', { duration: 2000 });
      });
    });
  }

  onStepDropped(event: CdkDragDrop<JobStep[]>): void {
    moveItemInArray(this.steps, event.previousIndex, event.currentIndex);
    this.steps.forEach((s, i) => s.stepOrder = i + 1);
    const stepIds = this.steps.map(s => s.stepId);
    this.jobService.reorderSteps(this.jobId!, stepIds).subscribe();
  }

  // --- Tab 3: Env Vars ---

  addEnvVar(): void {
    if (!this.newVarName.trim() || !this.newVarValue.trim()) return;
    this.jobService.addEnvVar(this.jobId!, {
      varName: this.newVarName.trim(), varValue: this.newVarValue.trim()
    }).subscribe(resp => {
      this.envVars = [...this.envVars, resp.data];
      this.newVarName = '';
      this.newVarValue = '';
    });
  }

  deleteEnvVar(v: EnvVar): void {
    this.jobService.deleteEnvVar(this.jobId!, v.envId).subscribe(() => {
      this.envVars = this.envVars.filter(e => e.envId !== v.envId);
    });
  }
}
```

```html
<!-- features/jobs/job-detail/job-detail.component.html -->

<div class="detail-container">
  <div class="detail-header">
    <button mat-icon-button routerLink="/jobs"><mat-icon>arrow_back</mat-icon></button>
    <h2>{{ isNew ? 'New Job' : job?.jobName }}</h2>
  </div>

  <mat-tab-group>

    <!-- TAB 1: GENERAL -->
    <mat-tab label="General">
      <form [formGroup]="generalForm" class="tab-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Job Name</mat-label>
          <input matInput formControlName="jobName">
          <mat-error *ngIf="generalForm.get('jobName')?.hasError('required')">Required</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <div class="path-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Working Directory</mat-label>
            <input matInput formControlName="workingDir" placeholder="/opt/jobs/my-job">
            <mat-error *ngIf="generalForm.get('workingDir')?.hasError('required')">Required</mat-error>
          </mat-form-field>
          <button mat-stroked-button (click)="validatePaths()" type="button">Validate</button>
        </div>

        <div *ngIf="pathValidation['workingDir']" class="validation-result"
             [class.ok]="pathValidation['workingDir'] === 'OK'"
             [class.error]="pathValidation['workingDir'] !== 'OK'">
          workingDir: {{ pathValidation['workingDir'] }}
        </div>

        <button mat-raised-button color="primary"
                (click)="saveGeneral()" [disabled]="generalForm.invalid || saving">
          {{ isNew ? 'Create Job' : 'Save' }}
        </button>
      </form>
    </mat-tab>

    <!-- TAB 2: STEPS -->
    <mat-tab label="Steps ({{ steps.length }})">
      <div class="tab-content">
        <button mat-raised-button color="primary" (click)="openAddStep()">
          <mat-icon>add</mat-icon> Add Step
        </button>

        <div cdkDropList (cdkDropListDropped)="onStepDropped($event)" class="step-list">
          <div *ngFor="let step of steps" cdkDrag class="step-row">
            <mat-icon cdkDragHandle class="drag-handle">drag_indicator</mat-icon>
            <span class="step-order">{{ step.stepOrder }}.</span>
            <span class="step-name">{{ step.stepName }}</span>
            <span class="step-type chip">{{ step.stepType }}</span>
            <span class="spacer"></span>
            <button mat-icon-button (click)="openEditStep(step)" matTooltip="Edit">
              <mat-icon>edit</mat-icon>
            </button>
            <button mat-icon-button color="warn" (click)="deleteStep(step)" matTooltip="Delete">
              <mat-icon>delete</mat-icon>
            </button>
          </div>
          <div *ngIf="steps.length === 0" class="empty-state">
            No steps yet. Click Add Step to begin.
          </div>
        </div>
      </div>
    </mat-tab>

    <!-- TAB 3: ENV VARS -->
    <mat-tab label="Env Vars ({{ envVars.length }})">
      <div class="tab-content">
        <div class="add-var-row">
          <input [(ngModel)]="newVarName"  placeholder="VAR_NAME"  class="var-input">
          <input [(ngModel)]="newVarValue" placeholder="value"     class="var-input">
          <button mat-raised-button (click)="addEnvVar()">Add</button>
        </div>
        <table class="env-table">
          <tr *ngFor="let v of envVars">
            <td class="mono">{{ v.varName }}</td>
            <td class="mono">{{ v.varValue }}</td>
            <td>
              <button mat-icon-button color="warn" (click)="deleteEnvVar(v)">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </tr>
        </table>
      </div>
    </mat-tab>

    <!-- TAB 4: SCHEDULE -->
    <mat-tab label="Schedule">
      <div class="tab-content" *ngIf="!isNew; else saveFirst">
        <app-schedule-editor [jobId]="jobId!" />
      </div>
      <ng-template #saveFirst>
        <p class="tab-content hint">Save the job first to configure a schedule.</p>
      </ng-template>
    </mat-tab>

  </mat-tab-group>
</div>
```

---

## 5c.2 Step Form Dialog — Dynamic Fields per StepType

```typescript
// features/jobs/step-form/step-form-dialog.component.ts

@Component({
  selector: 'app-step-form-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSlideToggleModule, MatButtonModule,
    MatChipsModule, MatIconModule
  ],
  templateUrl: './step-form-dialog.component.html'
})
export class StepFormDialogComponent implements OnInit {

  stepTypes: StepType[] = ['ENV_SETUP', 'LOG_CLEANUP', 'JAVA_EXEC', 'SFTP', 'ARCHIVE'];

  form = new FormGroup({
    stepName:          new FormControl('', Validators.required),
    stepType:          new FormControl<StepType>('JAVA_EXEC', Validators.required),
    continueOnFailure: new FormControl(false),
    enabled:           new FormControl(true),
    // ENV_SETUP
    javaHome:          new FormControl(''),
    classpath:         new FormControl(''),       // comma-separated, split on save
    // LOG_CLEANUP
    cleanupDir:        new FormControl(''),
    filePattern:       new FormControl(''),
    // JAVA_EXEC
    mainClass:         new FormControl(''),
    jarPath:           new FormControl(''),
    jvmArgs:           new FormControl(''),       // space-separated
    args:              new FormControl(''),       // space-separated
    timeoutMinutes:    new FormControl<number|null>(null),
    // SFTP
    host:              new FormControl(''),
    port:              new FormControl(22),
    username:          new FormControl(''),
    credentialRef:     new FormControl(''),
    remoteDir:         new FormControl(''),
    sftp_filePattern:  new FormControl(''),
    direction:         new FormControl<'UPLOAD'|'DOWNLOAD'>('UPLOAD'),
    // ARCHIVE
    sourceDir:         new FormControl(''),
    archiveDir:        new FormControl(''),
    archivePatterns:   new FormControl(''),       // comma-separated
    archiveFormat:     new FormControl<'ZIP'|'TAR_GZ'>('ZIP'),
  });

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { jobId: number; step: JobStep | null; nextOrder?: number },
    private dialogRef: MatDialogRef<StepFormDialogComponent>,
    private jobService: JobService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    if (this.data.step) {
      const s = this.data.step;
      this.form.patchValue({
        stepName:          s.stepName,
        stepType:          s.stepType,
        continueOnFailure: s.continueOnFailure,
        enabled:           s.enabled,
        ...this.configToFormValues(s.stepType, s.stepConfig)
      });
    }
  }

  get selectedType(): StepType {
    return this.form.value.stepType as StepType;
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.value;

    const stepConfig = JSON.stringify(this.formValuesToConfig(v));
    const payload = {
      stepName:          v.stepName!,
      stepOrder:         this.data.step?.stepOrder ?? this.data.nextOrder ?? 1,
      stepType:          v.stepType!,
      stepConfig,
      continueOnFailure: v.continueOnFailure!,
      enabled:           v.enabled!
    };

    const call = this.data.step
      ? this.jobService.updateStep(this.data.jobId, this.data.step.stepId, payload)
      : this.jobService.addStep(this.data.jobId, payload);

    call.subscribe({
      next: resp => this.dialogRef.close(resp.data),
      error: () => this.snack.open('Failed to save step', 'Dismiss', { duration: 3000 })
    });
  }

  cancel(): void { this.dialogRef.close(null); }

  // ---- Config ↔ Form conversion ----

  private configToFormValues(type: StepType, configJson: string): Record<string, unknown> {
    try {
      const c = JSON.parse(configJson);
      switch (type) {
        case 'ENV_SETUP':    return { javaHome: c.javaHome, classpath: (c.classpathEntries ?? []).join(',') };
        case 'LOG_CLEANUP':  return { cleanupDir: c.directory, filePattern: c.filePattern };
        case 'JAVA_EXEC':    return { mainClass: c.mainClass ?? '', jarPath: c.jarPath ?? '',
                               jvmArgs: (c.jvmArgs ?? []).join(' '), args: (c.args ?? []).join(' '),
                               timeoutMinutes: c.timeoutMinutes ?? null };
        case 'SFTP':         return { host: c.host, port: c.port, username: c.username,
                               credentialRef: c.credentialRef, remoteDir: c.remoteDir,
                               sftp_filePattern: c.filePattern, direction: c.direction };
        case 'ARCHIVE':      return { sourceDir: c.sourceDir, archiveDir: c.archiveDir,
                               archivePatterns: (c.filePatterns ?? []).join(','),
                               archiveFormat: c.archiveFormat };
      }
    } catch { return {}; }
    return {};
  }

  private formValuesToConfig(v: typeof this.form.value): Record<string, unknown> {
    switch (v.stepType) {
      case 'ENV_SETUP':
        return {
          javaHome: v.javaHome,
          classpathEntries: (v.classpath ?? '').split(',').map(s => s.trim()).filter(Boolean),
          extraEnvVars: {}
        };
      case 'LOG_CLEANUP':
        return { directory: v.cleanupDir, filePattern: v.filePattern };
      case 'JAVA_EXEC':
        return {
          mainClass: v.mainClass || null,
          jarPath:   v.jarPath   || null,
          jvmArgs:   (v.jvmArgs ?? '').split(' ').filter(Boolean),
          args:      (v.args    ?? '').split(' ').filter(Boolean),
          timeoutMinutes: v.timeoutMinutes || null
        };
      case 'SFTP':
        return {
          host: v.host, port: v.port, username: v.username,
          credentialRef: v.credentialRef, remoteDir: v.remoteDir,
          filePattern: v.sftp_filePattern, direction: v.direction
        };
      case 'ARCHIVE':
        return {
          sourceDir:    v.sourceDir,
          archiveDir:   v.archiveDir,
          filePatterns: (v.archivePatterns ?? '').split(',').map(s => s.trim()).filter(Boolean),
          archiveFormat: v.archiveFormat
        };
      default: return {};
    }
  }
}
```

```html
<!-- features/jobs/step-form/step-form-dialog.component.html -->

<h2 mat-dialog-title>{{ data.step ? 'Edit Step' : 'Add Step' }}</h2>

<mat-dialog-content [formGroup]="form">

  <!-- Common fields -->
  <mat-form-field appearance="outline" class="full-width">
    <mat-label>Step Name</mat-label>
    <input matInput formControlName="stepName">
  </mat-form-field>

  <mat-form-field appearance="outline">
    <mat-label>Step Type</mat-label>
    <mat-select formControlName="stepType">
      <mat-option *ngFor="let t of stepTypes" [value]="t">{{ t }}</mat-option>
    </mat-select>
  </mat-form-field>

  <mat-slide-toggle formControlName="continueOnFailure">Continue on failure</mat-slide-toggle>
  <mat-slide-toggle formControlName="enabled" style="margin-left:16px">Enabled</mat-slide-toggle>

  <hr>

  <!-- ENV_SETUP -->
  <ng-container *ngIf="selectedType === 'ENV_SETUP'">
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>JAVA_HOME</mat-label>
      <input matInput formControlName="javaHome" placeholder="/usr/lib/jvm/java-21">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Classpath Entries (comma-separated)</mat-label>
      <input matInput formControlName="classpath"
             placeholder="/opt/libs/app.jar,/opt/libs/deps.jar">
    </mat-form-field>
  </ng-container>

  <!-- LOG_CLEANUP -->
  <ng-container *ngIf="selectedType === 'LOG_CLEANUP'">
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Directory</mat-label>
      <input matInput formControlName="cleanupDir" placeholder="/opt/jobs/my-job/logs">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>File Pattern (glob)</mat-label>
      <input matInput formControlName="filePattern" placeholder="*.log">
      <mat-hint>Examples: *.log · job_*.txt · *.{log,out}</mat-hint>
    </mat-form-field>
  </ng-container>

  <!-- JAVA_EXEC -->
  <ng-container *ngIf="selectedType === 'JAVA_EXEC'">
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Main Class (or leave blank if using JAR)</mat-label>
      <input matInput formControlName="mainClass" placeholder="com.example.MainJob">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>JAR Path (or leave blank if using Main Class)</mat-label>
      <input matInput formControlName="jarPath" placeholder="/opt/jobs/my-job/app.jar">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>JVM Args (space-separated)</mat-label>
      <input matInput formControlName="jvmArgs" placeholder="-Xmx2g -Dspring.profiles.active=prod">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Program Args (space-separated)</mat-label>
      <input matInput formControlName="args" placeholder="--date=today --output=/tmp">
    </mat-form-field>
    <mat-form-field appearance="outline">
      <mat-label>Timeout (minutes, blank = default)</mat-label>
      <input matInput type="number" formControlName="timeoutMinutes">
    </mat-form-field>
  </ng-container>

  <!-- SFTP -->
  <ng-container *ngIf="selectedType === 'SFTP'">
    <mat-form-field appearance="outline"><mat-label>Host</mat-label>
      <input matInput formControlName="host"></mat-form-field>
    <mat-form-field appearance="outline"><mat-label>Port</mat-label>
      <input matInput type="number" formControlName="port"></mat-form-field>
    <mat-form-field appearance="outline" class="full-width"><mat-label>Username</mat-label>
      <input matInput formControlName="username"></mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Credential Reference</mat-label>
      <input matInput formControlName="credentialRef" placeholder="my-sftp-cred">
      <mat-hint>Name of a stored credential (configured in Global Config)</mat-hint>
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width"><mat-label>Remote Directory</mat-label>
      <input matInput formControlName="remoteDir"></mat-form-field>
    <mat-form-field appearance="outline" class="full-width"><mat-label>File Pattern</mat-label>
      <input matInput formControlName="sftp_filePattern" placeholder="*.pdf"></mat-form-field>
    <mat-form-field appearance="outline">
      <mat-label>Direction</mat-label>
      <mat-select formControlName="direction">
        <mat-option value="UPLOAD">Upload</mat-option>
        <mat-option value="DOWNLOAD">Download</mat-option>
      </mat-select>
    </mat-form-field>
  </ng-container>

  <!-- ARCHIVE -->
  <ng-container *ngIf="selectedType === 'ARCHIVE'">
    <mat-form-field appearance="outline" class="full-width"><mat-label>Source Directory</mat-label>
      <input matInput formControlName="sourceDir"></mat-form-field>
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>File Patterns (comma-separated)</mat-label>
      <input matInput formControlName="archivePatterns" placeholder="*.log,*.out">
    </mat-form-field>
    <mat-form-field appearance="outline" class="full-width"><mat-label>Archive Directory</mat-label>
      <input matInput formControlName="archiveDir"></mat-form-field>
    <mat-form-field appearance="outline">
      <mat-label>Format</mat-label>
      <mat-select formControlName="archiveFormat">
        <mat-option value="ZIP">ZIP</mat-option>
        <mat-option value="TAR_GZ">TAR_GZ</mat-option>
      </mat-select>
    </mat-form-field>
  </ng-container>

</mat-dialog-content>

<mat-dialog-actions align="end">
  <button mat-button (click)="cancel()">Cancel</button>
  <button mat-raised-button color="primary" (click)="save()" [disabled]="form.invalid">
    {{ data.step ? 'Update' : 'Add' }}
  </button>
</mat-dialog-actions>
```

---

## 5c.3 Schedule Editor Component

```typescript
// features/jobs/schedule-editor/schedule-editor.component.ts

@Component({
  selector: 'app-schedule-editor',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatSlideToggleModule, MatSnackBarModule
  ],
  templateUrl: './schedule-editor.component.html'
})
export class ScheduleEditorComponent implements OnInit {
  @Input() jobId!: number;

  schedule: JobSchedule | null = null;
  cronControl = new FormControl('0 0 2 * * *', Validators.required);
  nextFires: string[] = [];
  validating = false;

  constructor(
    private jobService: JobService,
    private http: HttpClient,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.jobService.getSchedule(this.jobId).subscribe(resp => {
      this.schedule = resp.data;
      if (this.schedule) {
        this.cronControl.setValue(this.schedule.cronExpression);
        this.validateCron();
      }
    });
    // Validate on every change
    this.cronControl.valueChanges.pipe(debounceTime(500)).subscribe(() => this.validateCron());
  }

  validateCron(): void {
    this.validating = true;
    this.http.get<ApiResponse<{ valid: boolean; nextFires?: string[]; error?: string }>>(
      '/api/system/cron-validate',
      { params: { expression: this.cronControl.value! } }
    ).subscribe(resp => {
      this.validating = false;
      this.nextFires = resp.data.valid ? (resp.data.nextFires ?? []) : [];
      if (!resp.data.valid) {
        this.cronControl.setErrors({ invalidCron: resp.data.error });
      } else {
        this.cronControl.setErrors(null);
      }
    });
  }

  save(): void {
    if (this.cronControl.invalid) return;
    const expr = this.cronControl.value!;
    const call = this.schedule
      ? this.jobService.updateSchedule(this.jobId, expr)
      : this.jobService.createSchedule(this.jobId, expr);

    call.subscribe(resp => {
      this.schedule = resp.data;
      this.snack.open('Schedule saved', 'OK', { duration: 2000 });
    });
  }

  deleteSchedule(): void {
    this.jobService.deleteSchedule(this.jobId).subscribe(() => {
      this.schedule = null;
      this.nextFires = [];
      this.snack.open('Schedule removed', 'OK', { duration: 2000 });
    });
  }

  toggleEnabled(): void {
    if (!this.schedule) return;
    const call = this.schedule.enabled
      ? this.jobService.disableSchedule(this.jobId)
      : this.jobService.enableSchedule(this.jobId);
    call.subscribe(resp => { this.schedule = resp.data; });
  }
}
```

```html
<!-- schedule-editor.component.html -->
<div class="schedule-editor">
  <mat-form-field appearance="outline" class="full-width">
    <mat-label>Cron Expression (6-field, Spring format)</mat-label>
    <input matInput [formControl]="cronControl" placeholder="0 0 2 * * *">
    <mat-hint>Seconds Minutes Hours Day Month Weekday</mat-hint>
    <mat-error *ngIf="cronControl.hasError('invalidCron')">
      {{ cronControl.getError('invalidCron') }}
    </mat-error>
  </mat-form-field>

  <div *ngIf="nextFires.length" class="next-fires">
    Next: {{ nextFires[0] }} · {{ nextFires[1] }} · {{ nextFires[2] }}
  </div>

  <div class="schedule-actions">
    <button mat-raised-button color="primary" (click)="save()"
            [disabled]="cronControl.invalid">
      {{ schedule ? 'Update Schedule' : 'Set Schedule' }}
    </button>
    <button mat-stroked-button (click)="toggleEnabled()" *ngIf="schedule">
      {{ schedule.enabled ? 'Disable' : 'Enable' }}
    </button>
    <button mat-stroked-button color="warn" (click)="deleteSchedule()" *ngIf="schedule">
      Remove Schedule
    </button>
  </div>

  <div *ngIf="schedule" class="schedule-status">
    Status: <strong>{{ schedule.enabled ? 'Active' : 'Disabled' }}</strong>
    · Expression: <code>{{ schedule.cronExpression }}</code>
  </div>
</div>
```

---

## Phase 5c Acceptance Criteria

- [ ] Navigating to `/jobs/new` shows an empty General tab; saving creates the job and redirects to `/jobs/{id}`
- [ ] General tab pre-populates for an existing job; saving updates it in place
- [ ] Path Validate button calls `/api/system/env-validate` and shows per-field results
- [ ] Steps tab shows all steps in `stepOrder` order
- [ ] Drag-and-drop reorder calls the reorder API; page reflects new order immediately
- [ ] Add Step opens dialog with no pre-filled fields; selecting a StepType shows the correct form fields
- [ ] Edit Step pre-fills all fields from the stored `stepConfig` JSON
- [ ] Step type switch clears old-type fields (form reset on type change)
- [ ] Env Vars tab adds and deletes variables with instant API calls
- [ ] Schedule tab shows next 3 fire times while typing; invalid cron shows error message
- [ ] Schedule enable/disable updates the badge without page reload

---

**Previous:** [Phase 5b — Dashboard & Job List](./PHASE-5b-UI-Dashboard-JobList.md)  
**Next:** [Phase 5d — Run Monitor & Log Viewer](./PHASE-5d-UI-RunMonitor-LogViewer.md)
