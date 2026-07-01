import { Component, inject } from '@angular/core';
import { MatDialogRef, MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { JobDefinition } from '../../../core/models/job.model';

export interface RunJobDialogData {
  job: JobDefinition;
}

@Component({
  selector: 'app-run-job-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatChipsModule],
  templateUrl: './run-job-dialog.html',
  styleUrl: './run-job-dialog.scss',
})
export class RunJobDialog {
  dialogRef = inject(MatDialogRef<RunJobDialog>);
  data = inject(MAT_DIALOG_DATA) as RunJobDialogData;

  get jobName(): string {
    return this.data.job.jobName;
  }

  get description(): string | null {
    return this.data.job.description;
  }

  get stepCount(): number {
    return this.data.job.steps?.filter(s => s.enabled).length ?? 0;
  }

  get totalSteps(): number {
    return this.data.job.steps?.length ?? 0;
  }

  get disabledStepCount(): number {
    return this.totalSteps - this.stepCount;
  }

  get hasSchedule(): boolean {
    return this.data.job.schedule !== null && this.data.job.schedule !== undefined;
  }

  get cronExpression(): string {
    return this.data.job.schedule?.cronExpression ?? '';
  }

  get isDisabled(): boolean {
    return !this.data.job.enabled;
  }

  confirm(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
