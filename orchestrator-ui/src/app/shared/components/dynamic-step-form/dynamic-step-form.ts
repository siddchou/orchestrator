import { Component, Input, OnInit, OnChanges, SimpleChanges, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { StepConfigSchema, FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';
import { DynamicFieldComponent } from '../dynamic-field/dynamic-field';

@Component({
  selector: 'app-dynamic-step-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DynamicFieldComponent],
  templateUrl: './dynamic-step-form.html',
  styleUrl: './dynamic-step-form.scss',
})
export class DynamicStepFormComponent implements OnInit, OnChanges {
  private fb = inject(FormBuilder);

  @Input() schema!: StepConfigSchema;
  @Input() existingConfig: Record<string, unknown> | null = null;
  @Input() credentials: Credential[] = [];

  form!: FormGroup;
  touchedFields = new Set<string>();

  ngOnInit(): void {
    this.form = this.buildForm();
  }

  ngOnChanges(changes: SimpleChanges): void {
    const schemaChanged = changes['schema'];
    const configChanged = changes['existingConfig'];

    if (schemaChanged) {
      // Check if field structure actually changed (different fields → rebuild form)
      const oldFields = schemaChanged.previousValue?.fields ?? [];
      const newFields = this.schema.fields;
      const structureChanged = oldFields.length !== newFields.length ||
        oldFields.some((f, i) => f.name !== newFields[i]?.name);

      if (structureChanged) {
        this.touchedFields.clear();
        this.form = this.buildForm();
      } else {
        // Same fields, different validators/defaults — update in place
        for (const field of newFields) {
          const ctrl = this.form.get(field.name);
          if (ctrl) {
            ctrl.setValue(this.resolveInitialValue(field));
            ctrl.setValidators(this.fieldValidators(field));
            ctrl.updateValueAndValidity({ emitEvent: false });
          }
        }
      }
    } else if (configChanged) {
      // Config changed but schema is the same — update values in place
      for (const field of this.schema.fields) {
        const ctrl = this.form.get(field.name);
        if (ctrl) {
          ctrl.setValue(this.resolveInitialValue(field));
        }
      }
    }

    // E6: credentials loaded async — re-validate SECRET_REF fields so the missing-credential error clears
    if (changes['credentials']) {
      for (const field of this.schema.fields) {
        if (field.type === 'SECRET_REF') {
          const ctrl = this.form.get(field.name);
          ctrl?.updateValueAndValidity();
        }
      }
    }
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
      if (val === null || val === undefined) return field.type === 'NUMBER' ? 0 : field.type === 'BOOLEAN' ? false : '';
      if (field.type === 'LIST_STRING') {
        if (Array.isArray(val)) return val.join(', ');
        return String(val);
      }
      if (field.type === 'STRING' || field.type === 'FILE_PATTERN' || field.type === 'ENUM' || field.type === 'SECRET_REF') {
        return String(val);
      }
      if (field.type === 'NUMBER') return Number(val) || 0;
      if (field.type === 'BOOLEAN') return Boolean(val);
    }
    if (field.defaultValue !== undefined && field.defaultValue !== null) {
      if (field.type === 'NUMBER') return Number(field.defaultValue) || 0;
      if (field.type === 'BOOLEAN') return Boolean(field.defaultValue);
      return String(field.defaultValue);
    }
    if (field.type === 'NUMBER') return 0;
    if (field.type === 'BOOLEAN') return false;
    return '';
  }

  private fieldValidators(field: FieldDefinition): any[] {
    const v: any[] = [];
    if (field.required) v.push(Validators.required);
    // E6: SECRET_REF — validate the referenced credential still exists
    if (field.type === 'SECRET_REF') {
      v.push((ctrl) => {
        if (!ctrl.value || !this.credentials.length) return null;
        const exists = this.credentials.some(c => c.ref === ctrl.value);
        return exists ? null : { missingCredential: true };
      });
    }
    return v;
  }

  /** Mark all controls touched. Returns true if the form is valid after touching. */
  validate(): boolean {
    Object.values(this.form.controls).forEach(c => c.markAsTouched());
    this.touchedFields = new Set(Object.keys(this.form.controls));
    return this.form.valid;
  }

  /** Extract the form values as a plain config object suitable for JSON.stringify */
  toConfig(): { config: Record<string, unknown>; valid: boolean } {
    const raw = { ...this.form.value };
    for (const field of this.schema.fields) {
      if (field.type === 'LIST_STRING' && raw[field.name] !== undefined) {
        const val: string = raw[field.name] as string;
        raw[field.name] = val
          ? val.split(',').map((s: string) => s.trim()).filter(Boolean)
          : [];
      }
    }
    return { config: raw, valid: this.form.valid };
  }

  get fields(): FieldDefinition[] {
    return this.schema.fields;
  }
}
