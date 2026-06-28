import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StepType } from '../../../core/models/job.model';

export interface StepFormData {
  stepId?: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;
  continueOnFailure: boolean;
  enabled: boolean;
}

@Component({
  selector: 'app-step-form-dialog',
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule, MatCheckboxModule, MatDialogModule,
  ],
  templateUrl: './step-form-dialog.html',
  styleUrl: './step-form-dialog.scss',
})
export class StepFormDialog {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<StepFormDialog>);
  data = inject<StepFormData>(MAT_DIALOG_DATA);

  stepTypes: StepType[] = ['ENV_SETUP', 'LOG_CLEANUP', 'JAVA_EXEC', 'SFTP', 'ARCHIVE'];
  form: FormGroup;

  constructor() {
    this.form = this.fb.group({
      stepName: [this.data.stepName, Validators.required],
      stepType: [this.data.stepType, Validators.required],
      stepConfig: [this.data.stepConfig || '{}'],
      continueOnFailure: [this.data.continueOnFailure],
      enabled: [this.data.enabled],
    });
  }

  onSubmit() {
    if (this.form.invalid) return;
    this.dialogRef.close({
      stepId: this.data.stepId,
      ...this.form.value,
    });
  }

  onCancel() {
    this.dialogRef.close();
  }
}
