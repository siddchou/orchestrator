import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';
import { DynamicFieldComponent } from '../dynamic-field/dynamic-field';

@Component({
  selector: 'app-dynamic-config-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DynamicFieldComponent],
  templateUrl: './dynamic-config-form.html',
  styleUrl: './dynamic-config-form.scss',
})
export class DynamicConfigFormComponent implements OnInit, OnChanges {
  private fb = inject(FormBuilder);

  @Input() fields: FieldDefinition[] = [];
  @Input() existingConfig: Record<string, unknown> | null = null;
  @Input() credentials: Credential[] = [];
  @Input() title: string | null = null;

  @Output() formReady = new EventEmitter<FormGroup>();
  @Output() configValid = new EventEmitter<boolean>();

  form!: FormGroup;
  touchedFields = new Set<string>();

  ngOnInit(): void {
    if (this.fields.length > 0 && !this.form) {
      this.form = this.buildForm(this.fields);
      this.formReady.emit(this.form);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    const fieldsChanged = changes['fields'];
    const configChanged = changes['existingConfig'];

    if (fieldsChanged && !fieldsChanged.firstChange) {
      const oldFields = fieldsChanged.previousValue ?? [];
      const newFields = this.fields;
      const structureChanged = oldFields.length !== newFields.length ||
        oldFields.some((f: FieldDefinition, i: number) => f.name !== newFields[i]?.name);

      if (structureChanged) {
        this.touchedFields.clear();
        this.form = this.buildForm(newFields);
        this.formReady.emit(this.form);
      } else {
        for (const field of newFields) {
          const ctrl = this.form.get(field.name);
          if (ctrl) {
            ctrl.setValue(this.resolveInitialValue(field));
            ctrl.setValidators(this.fieldValidators(field));
            ctrl.updateValueAndValidity({ emitEvent: false });
          }
        }
      }
    } else if (configChanged && !configChanged.firstChange) {
      for (const field of this.fields) {
        const ctrl = this.form.get(field.name);
        if (ctrl) {
          ctrl.setValue(this.resolveInitialValue(field));
        }
      }
    }

    if (changes['credentials'] && this.form && !changes['credentials'].firstChange) {
      for (const field of this.fields) {
        if (field.type === 'SECRET_REF') {
          const ctrl = this.form.get(field.name);
          ctrl?.updateValueAndValidity();
        }
      }
    }
  }

  buildForm(fields: FieldDefinition[]): FormGroup {
    const group: Record<string, any> = {};
    for (const field of fields) {
      const validators = this.fieldValidators(field);
      const initialValue = this.resolveInitialValue(field);
      group[field.name] = this.fb.control(initialValue, validators);
    }
    return this.fb.group(group);
  }

  resolveInitialValue(field: FieldDefinition): string | number | boolean {
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

  fieldValidators(field: FieldDefinition): any[] {
    const v: any[] = [];
    if (field.required) v.push(Validators.required);
    if (field.type === 'SECRET_REF') {
      v.push((ctrl) => {
        if (!ctrl.value || !this.credentials.length) return null;
        const exists = this.credentials.some(c => c.ref === ctrl.value);
        return exists ? null : { missingCredential: true };
      });
    }
    return v;
  }

  validate(): boolean {
    Object.values(this.form.controls).forEach(c => c.markAsTouched());
    this.touchedFields = new Set(Object.keys(this.form.controls));
    const valid = this.form.valid;
    this.configValid.emit(valid);
    return valid;
  }

  toConfig(): { config: Record<string, unknown>; valid: boolean } {
    const raw = { ...this.form.value };
    for (const field of this.fields) {
      if (field.type === 'LIST_STRING' && raw[field.name] !== undefined) {
        const val: string = raw[field.name] as string;
        raw[field.name] = val
          ? val.split(',').map((s: string) => s.trim()).filter(Boolean)
          : [];
      }
    }
    return { config: raw, valid: this.form.valid };
  }
}
