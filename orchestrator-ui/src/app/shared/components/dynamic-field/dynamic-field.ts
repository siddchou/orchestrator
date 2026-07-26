import { Component, Input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { FieldDefinition, FieldType } from '@app/core/models/job.model';

@Component({
  selector: 'app-dynamic-field',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatChipsModule],
  templateUrl: './dynamic-field.html',
  styleUrl: './dynamic-field.scss',
})
export class DynamicFieldComponent {
  @Input() fieldDef!: FieldDefinition;
  @Input() control!: FormControl;

  get fieldType(): FieldType {
    return this.fieldDef.type;
  }

  get placeholder(): string {
    if (this.fieldDef.helpText) return this.fieldDef.helpText;
    if (this.fieldDef.enumValues) return `Select ${this.fieldDef.label}`;
    return this.fieldDef.label;
  }

  /** Add a chip item from the input field */
  onAddChip(event: KeyboardEvent): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.trim();
    if (val) {
      const current = (this.control.value ?? '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean);
      if (!current.includes(val)) {
        current.push(val);
        this.control.setValue(current.length ? current.join(', ') : '');
      }
      input.value = '';
    }
  }

  /** Remove a chip item */
  removeChip(index: number): void {
    const current = (this.control.value ?? '')
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean);
    current.splice(index, 1);
    this.control.setValue(current.length ? current.join(', ') : '');
  }

  /** Get chip values from the comma-separated control value */
  get chips(): string[] {
    return (this.control.value ?? '')
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean);
  }
}
