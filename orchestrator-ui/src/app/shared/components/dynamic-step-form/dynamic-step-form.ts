import { Component, Input, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { StepConfigSchema, FieldDefinition } from '@app/core/models/job.model';
import { DynamicFieldComponent } from '../dynamic-field/dynamic-field';

@Component({
  selector: 'app-dynamic-step-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DynamicFieldComponent],
  templateUrl: './dynamic-step-form.html',
  styleUrl: './dynamic-step-form.scss',
})
export class DynamicStepFormComponent implements OnInit {
  private fb = inject(FormBuilder);

  @Input() schema!: StepConfigSchema;
  // Existing config JSON (from editing a step). If null, form starts with defaults.
  @Input() existingConfig: Record<string, unknown> | null = null;

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.buildForm();
  }

  private buildForm(): FormGroup {
    const group: Record<string, any> = {};
    for (const field of this.schema.fields) {
      const validators = this.fieldValidators(field);
      const initialValue = this.resolveInitialValue(field);
      group[field.name] = this.fb.control(initialValue, validators);
    }
    return this.fb.group(group);
  }

  private resolveInitialValue(field: FieldDefinition): string | number | boolean {
    if (this.existingConfig?.[field.name] !== undefined) {
      const val = this.existingConfig[field.name];
      // Coerce types for Material form controls (they expect string/number/boolean, not null)
      if (val === null || val === undefined) return field.type === 'NUMBER' ? 0 : field.type === 'BOOLEAN' ? false : '';
      if (field.type === 'LIST_STRING') {
        // Convert JSON array to comma-separated string for chip display
        if (Array.isArray(val)) return val.join(', ');
        return String(val);
      }
      if (field.type === 'STRING' || field.type === 'FILE_PATTERN' || field.type === 'ENUM' || field.type === 'SECRET_REF') {
        return String(val);
      }
      if (field.type === 'NUMBER') return Number(val) || 0;
      if (field.type === 'BOOLEAN') return Boolean(val);
    }
    // Use schema default
    if (field.defaultValue !== undefined && field.defaultValue !== null) {
      if (field.type === 'NUMBER') return Number(field.defaultValue) || 0;
      if (field.type === 'BOOLEAN') return Boolean(field.defaultValue);
      return String(field.defaultValue);
    }
    // Fallback by type
    if (field.type === 'NUMBER') return 0;
    if (field.type === 'BOOLEAN') return false;
    return '';
  }

  private fieldValidators(field: FieldDefinition): any[] {
    const v: any[] = [];
    if (field.required) v.push(Validators.required);
    return v;
  }

  /** Extract the form values as a plain config object suitable for JSON.stringify */
  toConfig(): Record<string, unknown> {
    if (!this.form.valid && this.schema.fields.some(f => f.required)) {
      // Mark all controls dirty so required errors surface
      Object.values(this.form.controls).forEach(c => c.markAsTouched());
    }
    const raw = { ...this.form.value };
    // Convert LIST_STRING fields from comma-separated string to JSON array
    for (const field of this.schema.fields) {
      if (field.type === 'LIST_STRING' && raw[field.name] !== undefined) {
        const val: string = raw[field.name] as string;
        raw[field.name] = val
          ? val.split(',').map((s: string) => s.trim()).filter(Boolean)
          : [];
      }
    }
    return raw;
  }

  get fields(): FieldDefinition[] {
    return this.schema.fields;
  }
}
