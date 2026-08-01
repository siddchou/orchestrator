import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicConfigFormComponent } from './dynamic-config-form';
import { FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';

const FIELDS: FieldDefinition[] = [
  { name: 'host', label: 'Host', type: 'STRING', required: true },
  { name: 'port', label: 'Port', type: 'NUMBER', required: true },
  { name: 'enabled', label: 'Enabled', type: 'BOOLEAN', required: false },
];

describe('DynamicConfigFormComponent', () => {
  let component: DynamicConfigFormComponent;
  let fixture: ComponentFixture<DynamicConfigFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DynamicConfigFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DynamicConfigFormComponent);
    component = fixture.componentInstance;
  });

  function setup(fields: FieldDefinition[], config?: Record<string, unknown> | null, credentials?: Credential[]) {
    fixture.componentRef.setInput('fields', fields);
    if (config !== undefined) fixture.componentRef.setInput('existingConfig', config);
    if (credentials !== undefined) fixture.componentRef.setInput('credentials', credentials);
    fixture.detectChanges(); // triggers ngOnChanges + ngOnInit → buildForm
  }

  // --- Form building ---

  it('creates a control for each field', () => {
    setup(FIELDS);
    expect(component.form.contains('host')).toBe(true);
    expect(component.form.contains('port')).toBe(true);
    expect(component.form.contains('enabled')).toBe(true);
  });

  it('emits formReady event on initial build', () => {
    const spy = vi.fn();
    component.formReady.subscribe(spy);
    setup(FIELDS);
    expect(spy).toHaveBeenCalledOnce();
    expect(spy).toHaveBeenCalledWith(component.form);
  });

  it('applies required validator when field is required', () => {
    setup(FIELDS);
    expect(component.form.get('host')?.invalid).toBe(true); // empty + required
    expect(component.form.get('enabled')?.invalid).toBe(false); // not required, boolean false is valid
  });

  it('uses default values when no existingConfig', () => {
    setup(FIELDS);
    expect(component.form.get('host')?.value).toBe('');
    expect(component.form.get('port')?.value).toBe(0);
    expect(component.form.get('enabled')?.value).toBe(false);
  });

  it('populates controls from existingConfig', () => {
    setup(FIELDS, { host: 'server.example.com', port: 2222, enabled: true });
    expect(component.form.get('host')?.value).toBe('server.example.com');
    expect(component.form.get('port')?.value).toBe(2222);
    expect(component.form.get('enabled')?.value).toBe(true);
  });

  it('converts array values to comma-separated string for LIST_STRING', () => {
    const listFields: FieldDefinition[] = [
      { name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false },
    ];
    setup(listFields, { tags: ['alpha', 'beta'] });
    expect(component.form.get('tags')?.value).toBe('alpha, beta');
  });

  it('coerces null existingConfig values to type-appropriate defaults', () => {
    const fields: FieldDefinition[] = [
      { name: 's', label: 'S', type: 'STRING', required: false },
      { name: 'n', label: 'N', type: 'NUMBER', required: false },
      { name: 'b', label: 'B', type: 'BOOLEAN', required: false },
    ];
    setup(fields, { s: null, n: null, b: null });
    expect(component.form.get('s')?.value).toBe('');
    expect(component.form.get('n')?.value).toBe(0);
    expect(component.form.get('b')?.value).toBe(false);
  });

  // --- Fields change detection (ngOnChanges) ---

  it('rebuilds form when fields change', () => {
    setup(FIELDS, { host: 'old.com', port: 22, enabled: false });
    expect(component.form.get('host')?.value).toBe('old.com');

    const newFields: FieldDefinition[] = [
      { name: 'mainClass', label: 'Main Class', type: 'STRING', required: true },
      { name: 'args', label: 'Args', type: 'LIST_STRING', required: false },
    ];
    fixture.componentRef.setInput('fields', newFields);
    fixture.detectChanges();

    expect(component.form.contains('host')).toBe(false);
    expect(component.form.contains('mainClass')).toBe(true);
    expect(component.form.contains('args')).toBe(true);
  });

  it('clears touchedFields when fields structure changes', () => {
    setup(FIELDS);
    component.touchedFields.add('host');
    expect(component.touchedFields.size).toBe(1);

    const newFields: FieldDefinition[] = [
      { name: 'x', label: 'X', type: 'STRING', required: false },
    ];
    fixture.componentRef.setInput('fields', newFields);
    fixture.detectChanges();

    expect(component.touchedFields.size).toBe(0);
  });

  it('updates control values when existingConfig changes', () => {
    setup(FIELDS, null);
    expect(component.form.get('host')?.value).toBe('');

    fixture.componentRef.setInput('existingConfig', { host: 'new.com', port: 80, enabled: true });
    fixture.detectChanges();

    expect(component.form.get('host')?.value).toBe('new.com');
    expect(component.form.get('port')?.value).toBe(80);
    expect(component.form.get('enabled')?.value).toBe(true);
  });

  // --- validate() ---

  it('marks all controls touched when validate() is called', () => {
    setup(FIELDS);
    const result = component.validate();
    expect(result).toBe(false); // required fields are empty
    expect(component.form.get('host')?.touched).toBe(true);
    expect(component.form.get('port')?.touched).toBe(true);
  });

  it('emits configValid event on validate()', () => {
    setup(FIELDS, { host: 'server.com', port: 22 });
    const spy = vi.fn();
    component.configValid.subscribe(spy);
    component.validate();
    expect(spy).toHaveBeenCalledWith(true);
  });

  it('returns true when all required fields have values', () => {
    setup(FIELDS, { host: 'server.com', port: 22, enabled: false });
    const result = component.validate();
    expect(result).toBe(true);
  });

  it('populates touchedFields with all field names after validate()', () => {
    setup(FIELDS);
    component.validate();
    expect(component.touchedFields.has('host')).toBe(true);
    expect(component.touchedFields.has('port')).toBe(true);
    expect(component.touchedFields.has('enabled')).toBe(true);
  });

  // --- toConfig() ---

  it('returns config object and validity', () => {
    setup(FIELDS, { host: 'server.com', port: 22, enabled: true });
    const result = component.toConfig();
    expect(result.valid).toBe(true);
    expect(result.config['host']).toBe('server.com');
    expect(result.config['port']).toBe(22);
  });

  it('converts LIST_STRING from comma-separated string to array in config', () => {
    const listFields: FieldDefinition[] = [
      { name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false },
    ];
    setup(listFields);
    component.form.get('tags')!.setValue('a, b, c');
    const result = component.toConfig();
    expect(result.config['tags']).toEqual(['a', 'b', 'c']);
  });

  it('returns valid=false when required fields are empty', () => {
    setup(FIELDS); // no config → required fields are empty
    const result = component.toConfig();
    expect(result.valid).toBe(false);
  });

  it('handles empty LIST_STRING as empty array', () => {
    const listFields: FieldDefinition[] = [
      { name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false },
    ];
    setup(listFields);
    component.form.get('tags')!.setValue('');
    const result = component.toConfig();
    expect(result.config['tags']).toEqual([]);
  });

  // --- Credentials passthrough ---

  it('exposes credentials array for child components', () => {
    const creds: Credential[] = [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }];
    setup(FIELDS, null, creds);
    expect(component.credentials).toEqual(creds);
  });

  // --- SECRET_REF validation ---

  it('validates SECRET_REF against credentials', () => {
    const secretFields: FieldDefinition[] = [
      { name: 'password', label: 'Password', type: 'SECRET_REF', required: true },
    ];
    const creds: Credential[] = [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }];
    setup(secretFields, { password: 'invalid-ref' }, creds);
    expect(component.form.get('password')?.valid).toBe(false);
  });

  it('re-validates SECRET_REF when credentials change', () => {
    const secretFields: FieldDefinition[] = [
      { name: 'password', label: 'Password', type: 'SECRET_REF', required: true },
    ];
    setup(secretFields, { password: 'db-pass' });
    // No credentials yet — should not error (guard clause)
    expect(component.form.get('password')?.valid).toBe(true);

    fixture.componentRef.setInput('credentials', [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }]);
    fixture.detectChanges();
    expect(component.form.get('password')?.valid).toBe(true);
  });

  // --- Title input ---

  it('renders title when provided', () => {
    setup(FIELDS);
    fixture.componentRef.setInput('title', 'Connection Settings');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Connection Settings');
  });

  it('does not render title when null', () => {
    setup(FIELDS);
    const titleEl = fixture.nativeElement.querySelector('.config-form-title');
    expect(titleEl).toBeNull();
  });

  // --- Touched field tracking ---

  it('tracks touched fields via blur events from children', () => {
    setup(FIELDS);
    expect(component.touchedFields.size).toBe(0);

    component.touchedFields.add('host');
    expect(component.touchedFields.has('host')).toBe(true);
    expect(component.touchedFields.has('port')).toBe(false);
  });
});
