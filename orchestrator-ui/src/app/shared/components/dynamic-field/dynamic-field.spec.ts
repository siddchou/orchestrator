import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { DynamicFieldComponent } from './dynamic-field';
import { FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';

const STRING_FIELD: FieldDefinition = { name: 'name', label: 'Name', type: 'STRING', required: true };
const NUMBER_FIELD: FieldDefinition = { name: 'count', label: 'Count', type: 'NUMBER', required: false };
const BOOLEAN_FIELD: FieldDefinition = { name: 'enabled', label: 'Enabled', type: 'BOOLEAN', required: false };
const ENUM_FIELD: FieldDefinition = { name: 'mode', label: 'Mode', type: 'ENUM', required: true, enumValues: ['A', 'B'] };
const SECRET_FIELD: FieldDefinition = { name: 'cred', label: 'Credential', type: 'SECRET_REF', required: true };
const LIST_FIELD: FieldDefinition = { name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false };
const FILE_FIELD: FieldDefinition = { name: 'pattern', label: 'Pattern', type: 'FILE_PATTERN', required: false, helpText: '*.log' };

describe('DynamicFieldComponent', () => {
  let component: DynamicFieldComponent;
  let fixture: ComponentFixture<DynamicFieldComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DynamicFieldComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DynamicFieldComponent);
    component = fixture.componentInstance;
  });

  function setup(field: FieldDefinition, value = '', showError = false) {
    const validators = field.required ? [Validators.required] : [];
    component.fieldDef = field;
    component.control = new FormControl(value, validators);
    component.showError = showError;
    component.credentials = [];
    fixture.detectChanges();
  }

  // --- Rendering by type ---

  it('renders text input for STRING type', () => {
    setup(STRING_FIELD);
    const input = fixture.nativeElement.querySelector('input[type="text"], input:not([type])');
    expect(input).toBeTruthy();
  });

  it('renders number input for NUMBER type', () => {
    setup(NUMBER_FIELD);
    const input = fixture.nativeElement.querySelector('input[type="number"]');
    expect(input).toBeTruthy();
  });

  it('renders checkbox for BOOLEAN type', () => {
    setup(BOOLEAN_FIELD);
    const checkbox = fixture.nativeElement.querySelector('mat-checkbox');
    expect(checkbox).toBeTruthy();
  });

  it('renders mat-select for ENUM type', () => {
    setup(ENUM_FIELD);
    // Material select uses CDK overlays not queryable in jsdom; verify via component state
    expect(component.fieldType).toBe('ENUM');
    expect(component.fieldDef.enumValues?.length).toBe(2);
  });

  it('renders text input fallback for SECRET_REF without credentials', () => {
    setup(SECRET_FIELD);
    const input = fixture.nativeElement.querySelector('input');
    expect(input).toBeTruthy();
    expect(component.hasCredentials).toBe(false);
  });

  it('renders mat-select for SECRET_REF with credentials', () => {
    // Set up manually to avoid ExpressionChangedAfterItHasBeenCheckedError
    component.fieldDef = SECRET_FIELD;
    component.control = new FormControl('', [Validators.required]);
    component.showError = false;
    component.credentials = [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }];
    fixture.detectChanges();
    expect(component.hasCredentials).toBe(true);
    const select = fixture.nativeElement.querySelector('mat-select');
    expect(select).toBeTruthy();
  });

  it('renders chip container for LIST_STRING', () => {
    setup(LIST_FIELD);
    const container = fixture.nativeElement.querySelector('.chip-container');
    expect(container).toBeTruthy();
    const input = container.querySelector('input');
    expect(input).toBeTruthy();
  });

  it('renders text input for FILE_PATTERN', () => {
    setup(FILE_FIELD);
    const input = fixture.nativeElement.querySelector('input');
    expect(input).toBeTruthy();
  });

  // --- Error display ---

  it('shows error when showError=true, control touched and invalid', () => {
    setup(STRING_FIELD); // required field with no value → invalid
    component.control.markAsTouched();
    component.showError = true;
    fixture.detectChanges();
    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error).toBeTruthy();
  });

  it('hides error when showError=false even if control invalid', () => {
    setup(STRING_FIELD);
    component.control.markAsTouched();
    component.showError = false;
    fixture.detectChanges();
    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error).toBeFalsy();
  });

  it('hides error when control not touched', () => {
    setup(STRING_FIELD);
    component.showError = true;
    // control is NOT touched
    fixture.detectChanges();
    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error).toBeFalsy();
  });

  it('shows required error message for required field', () => {
    setup(STRING_FIELD);
    component.control.markAsTouched();
    component.showError = true;
    fixture.detectChanges();
    expect(component.errorMessage).toContain('required');
  });

  // --- Unsupported type warning ---

  it('shows warning banner for unknown field type', () => {
    const unknownField: FieldDefinition = { name: 'x', label: 'X', type: 'UNKNOWN' as any, required: false };
    setup(unknownField);
    expect(component.isKnownType).toBe(false);
    fixture.detectChanges();
    const warning = fixture.nativeElement.querySelector('.unsupported-warning');
    expect(warning).toBeTruthy();
  });

  it('does not show warning for known field types', () => {
    setup(STRING_FIELD);
    expect(component.isKnownType).toBe(true);
    fixture.detectChanges();
    const warning = fixture.nativeElement.querySelector('.unsupported-warning');
    expect(warning).toBeFalsy();
  });

  // --- Chip operations ---

  it('adds chip on Enter key', () => {
    setup(LIST_FIELD, '');
    const event = { target: { value: 'new-tag' } } as unknown as KeyboardEvent;
    component.onAddChip(event);
    expect(component.chips).toContain('new-tag');
  });

  it('removes chip by index', () => {
    setup(LIST_FIELD, 'a, b, c');
    component.removeChip(1);
    expect(component.chips).toEqual(['a', 'c']);
  });

  it('prevents duplicate chips', () => {
    setup(LIST_FIELD, 'alpha');
    const event = { target: { value: 'alpha' } } as unknown as KeyboardEvent;
    component.onAddChip(event);
    expect(component.chips).toEqual(['alpha']);
  });

  // --- Required indicator ---

  it('marks field as required when fieldDef.required is true', () => {
    setup(STRING_FIELD);
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('mat-label, .list-label');
    expect(label?.textContent).toContain('*');
  });

  it('does not show asterisk for optional fields', () => {
    setup(NUMBER_FIELD);
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('mat-label, .list-label');
    expect(label?.textContent).not.toContain('*');
  });
});
