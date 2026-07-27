import { Component, ViewChild, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StepConfigSchema } from '../../../core/models/job.model';
import { Credential } from '../../../core/models/credential.model';
import { JobService } from '../../../core/services/job.service';
import { CredentialService } from '../../../core/services/credential.service';
import { DynamicStepFormComponent } from '../../../shared/components/dynamic-step-form/dynamic-step-form';

export interface StepFormData {
  stepId?: number;
  stepName: string;
  stepOrder: number;
  stepType: string;
  stepConfig: string;
  continueOnFailure: boolean;
  enabled: boolean;
}

@Component({
  selector: 'app-step-form-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatSlideToggleModule, MatDialogModule,
    DynamicStepFormComponent,
  ],
  templateUrl: './step-form-dialog.html',
  styleUrl: './step-form-dialog.scss',
})
export class StepFormDialog {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<StepFormDialog>);
  private jobService = inject(JobService);
  private credentialService = inject(CredentialService);
  data = inject<StepFormData>(MAT_DIALOG_DATA);

  @ViewChild(DynamicStepFormComponent) dynamicForm?: DynamicStepFormComponent;

  form: FormGroup;
  availableTypes: StepConfigSchema[] = [];
  loadingTypes = true;
  credentials: Credential[] = [];

  constructor() {
    this.form = this.fb.group({
      stepName: [this.data.stepName, Validators.required],
      stepType: [this.data.stepType, Validators.required],
      continueOnFailure: [this.data.continueOnFailure],
      enabled: [this.data.enabled],
    });
  }

  ngOnInit(): void {
    this.jobService.listStepTypes().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.availableTypes = res.data.sort((a, b) => a.displayName.localeCompare(b.displayName));
        }
        this.loadingTypes = false;
      },
      error: () => {
        this.loadingTypes = false;
      },
    });

    this.credentialService.listCredentials().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.credentials = res.data;
        }
      },
      error: () => {
        // Credentials optional — dialog still works without them
      },
    });
  }

  get selectedSchema(): StepConfigSchema | undefined {
    const type = this.form.get('stepType')?.value;
    return this.availableTypes.find(s => s.stepType === type);
  }

  get existingConfig(): Record<string, unknown> | null {
    if (!this.data.stepConfig) return null;
    try {
      return JSON.parse(this.data.stepConfig);
    } catch {
      return {};
    }
  }

  onSubmit() {
    if (this.form.invalid) return;

    // Validate the dynamic form — marks all controls touched, shows errors
    if (this.dynamicForm) {
      const isValid = this.dynamicForm.validate();
      if (!isValid) return;
    }

    let stepConfig = '{}';
    if (this.selectedSchema && this.dynamicForm) {
      const result = this.dynamicForm.toConfig();
      stepConfig = JSON.stringify(result.config);
    }

    this.dialogRef.close({
      stepId: this.data.stepId,
      stepName: this.form.value.stepName,
      stepOrder: this.data.stepOrder,
      stepType: this.form.value.stepType,
      stepConfig,
      continueOnFailure: this.form.value.continueOnFailure,
      enabled: this.form.value.enabled,
    });
  }

  onCancel() {
    this.dialogRef.close();
  }
}
