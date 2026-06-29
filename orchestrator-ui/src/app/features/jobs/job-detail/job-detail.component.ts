import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CdkDrag, CdkDropList, CdkDragHandle, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ActivatedRoute, Router } from '@angular/router';
import { JobService } from '../../../core/services/job.service';
import { SystemService } from '../../../core/services/system.service';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { StepFormDialog, StepFormData } from '../step-builder/step-form-dialog';
import { JobDefinition, JobStep, EnvVar, JobSchedule, StepType } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-detail',
  imports: [
    CommonModule, ReactiveFormsModule, FormsModule, MatTabsModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
    MatCheckboxModule, MatProgressSpinnerModule, MatSnackBarModule, MatDialogModule, MatChipsModule,
    MatTooltipModule, CdkDrag, CdkDropList, CdkDragHandle,
  ],
  templateUrl: './job-detail.component.html',
  styleUrl: './job-detail.component.scss',
})
export class JobDetailComponent implements OnInit {
  private fb = inject(FormBuilder);
  private jobService = inject(JobService);
  private systemService = inject(SystemService);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cd = inject(ChangeDetectorRef);

  jobId: number | null = null;
  job: JobDefinition | null = null;
  loading = true;
  scheduleNextFires: string[] = [];

  generalForm: FormGroup = this.fb.group({
    jobName: ['', Validators.required],
    description: [''],
    workingDir: ['', Validators.required],
  });

  displayedStepCols = ['stepOrder', 'stepName', 'stepType', 'continueOnFailure', 'enabled', 'actions'];
  displayedEnvCols = ['key', 'value', 'actions'];
  newEnvKey = '';
  newEnvValue = '';
  scheduleCron = '';
  scheduleEnabled = false;
  pathValidation: Record<string, string> = {};
  private cronDebounceTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.jobId = +idParam;
      this.loadJob();
    } else {
      this.loading = false;
      this.cd.detectChanges();
    }
  }

  loadJob() {
    if (this.jobId == null) return;
    this.jobService.getJob(this.jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.job = res.data;
          this.generalForm.patchValue({
            jobName: this.job.jobName,
            description: this.job.description,
            workingDir: this.job.workingDir,
          });
          if (this.job.schedule) {
            this.scheduleCron = this.job.schedule.cronExpression;
            this.scheduleEnabled = this.job.schedule.enabled;
            this.systemService.validateCron(this.scheduleCron).subscribe({
              next: (vRes) => {
                if (vRes.status === 'SUCCESS' && vRes.data.valid) {
                  this.scheduleNextFires = vRes.data.nextFireTimes;
                }
              },
            });
          }
        }
        this.loading = false;
        this.cd.detectChanges();
      },
      error: () => { this.loading = false; this.cd.detectChanges(); },
    });
  }

  saveGeneral() {
    if (this.generalForm.invalid || this.jobId == null) return;
    const val = this.generalForm.value;
    this.jobService.updateJob(this.jobId, {
      jobName: val.jobName,
      description: val.description,
      workingDir: val.workingDir,
    }).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.snackBar.open('Job updated', 'Dismiss', { duration: 3000 });
          this.loadJob();
        }
      },
    });
  }

  openStepForm(step?: JobStep) {
    const data: StepFormData = {
      stepId: step?.stepId,
      stepName: step?.stepName ?? '',
      stepOrder: step?.stepOrder ?? (this.job?.steps.length ?? 0),
      stepType: step?.stepType ?? ('JAVA_EXEC' as StepType),
      stepConfig: step?.stepConfig ?? '{}',
      continueOnFailure: step?.continueOnFailure ?? false,
      enabled: step?.enabled ?? true,
    };
    this.dialog.open(StepFormDialog, { data, width: '550px' }).afterClosed().subscribe(result => {
      if (!result) return;
      if (this.jobId == null) return;
      if (result.stepId) {
        this.jobService.updateStep(this.jobId, result.stepId, result).subscribe({
          next: () => this.loadJob(),
        });
      } else {
        this.jobService.addStep(this.jobId, result).subscribe({
          next: () => this.loadJob(),
        });
      }
    });
  }

  deleteStep(step: JobStep) {
    if (this.jobId == null) return;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Step',
        message: `Delete step "${step.stepName}"?`,
        confirmButton: 'Delete',
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed || this.jobId == null) return;
      this.jobService.deleteStep(this.jobId, step.stepId).subscribe({
        next: () => this.loadJob(),
      });
    });
  }

  addEnvVar() {
    if (!this.newEnvKey.trim() || !this.newEnvValue.trim() || this.jobId == null) return;
    this.jobService.addEnvVar(this.jobId, { key: this.newEnvKey, value: this.newEnvValue }).subscribe({
      next: () => {
        this.newEnvKey = '';
        this.newEnvValue = '';
        this.loadJob();
      },
    });
  }

  deleteEnvVar(envId: number) {
    if (this.jobId == null) return;
    this.jobService.deleteEnvVar(this.jobId, envId).subscribe({
      next: () => this.loadJob(),
    });
  }

  validateCron() {
    if (!this.scheduleCron) return;
    this.systemService.validateCron(this.scheduleCron).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          if (res.data.valid) {
            this.scheduleNextFires = res.data.nextFireTimes;
            this.snackBar.open('Cron expression is valid', 'Dismiss', { duration: 3000 });
          } else {
            this.snackBar.open('Invalid cron expression', 'Dismiss', { duration: 3000, panelClass: 'error-snackbar' });
          }
        }
      },
    });
  }

  onCronInput() {
    if (this.cronDebounceTimer) clearTimeout(this.cronDebounceTimer);
    this.cronDebounceTimer = setTimeout(() => this.validateCron(), 500);
  }

  saveSchedule() {
    if (this.jobId == null || !this.scheduleCron) return;
    if (this.job?.schedule) {
      this.jobService.updateSchedule(this.jobId, this.scheduleCron).subscribe({
        next: () => { this.loadJob(); this.snackBar.open('Schedule updated', 'Dismiss', { duration: 3000 }); },
      });
    } else {
      this.jobService.createSchedule(this.jobId, this.scheduleCron).subscribe({
        next: () => { this.loadJob(); this.snackBar.open('Schedule created', 'Dismiss', { duration: 3000 }); },
      });
    }
  }

  toggleSchedule() {
    if (this.jobId == null) return;
    if (this.scheduleEnabled) {
      this.jobService.disableSchedule(this.jobId).subscribe({ next: () => this.loadJob() });
    } else {
      this.jobService.enableSchedule(this.jobId).subscribe({ next: () => this.loadJob() });
    }
  }

  deleteSchedule() {
    if (this.jobId == null) return;
    this.dialog.open(ConfirmDialog, {
      data: { title: 'Delete Schedule', message: 'Remove the schedule for this job?', confirmButton: 'Delete' },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed || this.jobId == null) return;
      this.jobService.deleteSchedule(this.jobId).subscribe({ next: () => this.loadJob() });
    });
  }

  validatePaths() {
    const wd = this.generalForm.value.workingDir ?? '';
    this.systemService.validateEnv('', wd).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.pathValidation = { workingDir: res.data.message ?? 'OK' };
        }
      },
    });
  }

  onStepDropped(event: CdkDragDrop<JobStep[]>) {
    if (!this.job) return;
    moveItemInArray(this.job.steps, event.previousIndex, event.currentIndex);
    this.job.steps.forEach((s, i) => (s.stepOrder = i + 1));
    const stepIds = this.job.steps.map(s => s.stepId);
    this.jobService.reorderSteps(this.jobId!, stepIds).subscribe();
  }

  createNewJob() {
    if (this.generalForm.invalid) return;
    const val = this.generalForm.value;
    this.jobService.createJob({
      jobName: val.jobName,
      description: val.description,
      workingDir: val.workingDir,
    }).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.router.navigate(['/jobs', res.data.jobId]);
        }
      },
    });
  }

  goBack() {
    this.router.navigate(['/jobs']);
  }
}
