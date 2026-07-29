import { Component, Input, Output, EventEmitter } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { FieldDefinition, FieldType } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';

const KNOWN_TYPES = new Set<FieldType>([
  'STRING', 'NUMBER', 'BOOLEAN', 'ENUM', 'SECRET_REF', 'FILE_PATTERN', 'LIST_STRING'
]);

@Component({
  selector: 'app-dynamic-field',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCheckboxModule, MatChipsModule, MatIconModule
  ],
  templateUrl: './dynamic-field.html',
  styleUrl: './dynamic-field.scss',
})
export class DynamicFieldComponent {
  @Input() fieldDef!: FieldDefinition;
  @Input() control!: FormControl;
  /** When true and the control is invalid+touched, show a <mat-error> below the field */
  @Input() showError = false;
  /** Credentials to populate SECRET_REF dropdown. If empty, falls back to text input. */
  @Input() credentials: Credential[] = [];
  /** Emitted when any form field inside this component loses focus */
  @Output() blur = new EventEmitter<void>();

  get fieldType(): string {
    return this.fieldDef.type;
  }

  /** Whether the field's type is recognized by the known FieldType enum values */
  get isKnownType(): boolean {
    return KNOWN_TYPES.has(this.fieldDef.type as FieldType);
  }

  get placeholder(): string {
    if (this.fieldDef.helpText) return this.fieldDef.helpText;
    if (this.fieldDef.enumValues) return `Select ${this.fieldDef.label}`;
    return this.fieldDef.label;
  }

  /** Whether to display the error message for this field */
  get showFieldError(): boolean {
    return this.showError && this.control.touched && this.control.invalid;
  }

  /** Human-readable validation error message */
  get errorMessage(): string {
    if (!this.control.invalid) return '';
    if (this.control.errors?.['required']) {
      return `${this.fieldDef.label} is required`;
    }
    // E6: credential referenced by SECRET_REF no longer exists
    if (this.control.errors?.['missingCredential']) {
      const val = this.control.value;
      return `Referenced credential '${val}' no longer exists — select a valid credential.`;
    }
    return `${this.fieldDef.label} is invalid`;
  }

  /** Whether this SECRET_REF field should render as a credential dropdown */
  get hasCredentials(): boolean {
    return this.fieldType === 'SECRET_REF' && this.credentials.length > 0;
  }

  // --- Chip input helpers for LIST_STRING ---

  /** Add a chip item from the input field */
  onAddChip(event: KeyboardEvent): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.trim();
    if (val) {
      const current = this.getChipsFromValue();
      if (!current.includes(val)) {
        current.push(val);
        this.control.setValue(current.join(', '));
      }
      input.value = '';
    }
  }

  /** Remove a chip item */
  removeChip(index: number): void {
    const current = this.getChipsFromValue();
    current.splice(index, 1);
    this.control.setValue(current.join(', '));
  }

  /** Get chip values from the comma-separated control value */
  get chips(): string[] {
    return this.getChipsFromValue();
  }

  private getChipsFromValue(): string[] {
    return (this.control.value ?? '')
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean);
  }
}
