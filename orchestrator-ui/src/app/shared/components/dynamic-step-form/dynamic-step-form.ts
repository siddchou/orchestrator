import { Component, Input, ViewChild } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { StepConfigSchema, FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';
import { DynamicConfigFormComponent } from '../dynamic-config-form/dynamic-config-form';

@Component({
  selector: 'app-dynamic-step-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DynamicConfigFormComponent],
  templateUrl: './dynamic-step-form.html',
  styleUrl: './dynamic-step-form.scss',
})
export class DynamicStepFormComponent {
  @Input() schema!: StepConfigSchema;
  @Input() existingConfig: Record<string, unknown> | null = null;
  @Input() credentials: Credential[] = [];

  @ViewChild(DynamicConfigFormComponent) configForm!: DynamicConfigFormComponent;

  /** Proxy to child form — populated once the child emits formReady */
  form!: FormGroup;

  touchedFields = new Set<string>();

  onFormReady(group: FormGroup): void {
    this.form = group;
  }

  /** Mark all controls touched. Returns true if the form is valid after touching. */
  validate(): boolean {
    return this.configForm.validate();
  }

  /** Extract the form values as a plain config object suitable for JSON.stringify */
  toConfig(): { config: Record<string, unknown>; valid: boolean } {
    return this.configForm.toConfig();
  }

  get fields(): FieldDefinition[] {
    return this.schema?.fields ?? [];
  }
}
