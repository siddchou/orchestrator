import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicStepFormComponent } from './dynamic-step-form';
import { StepConfigSchema, FieldDefinition } from '@app/core/models/job.model';
import { Credential } from '@app/core/models/credential.model';

const FIELDS: FieldDefinition[] = [
  { name: 'host', label: 'Host', type: 'STRING', required: true },
  { name: 'port', label: 'Port', type: 'NUMBER', required: true },
  { name: 'enabled', label: 'Enabled', type: 'BOOLEAN', required: false },
];
const SCHEMA: StepConfigSchema = { stepType: 'SFTP', displayName: 'SFTP Transfer', fields: FIELDS };

describe('DynamicStepFormComponent', () => {
  let component: DynamicStepFormComponent;
  let fixture: ComponentFixture<DynamicStepFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DynamicStepFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DynamicStepFormComponent);
    component = fixture.componentInstance;
  });

  function setup(schema: StepConfigSchema, config?: Record<string, unknown> | null, credentials?: Credential[]) {
    component.schema = schema;
    component.existingConfig = config ?? null;
    component.credentials = credentials ?? [];
    fixture.detectChanges(); // triggers ngOnInit → buildForm
  }

  // --- Form building ---

  it('creates a control for each field in the schema', () => {
    setup(SCHEMA);
    expect(component.form.contains('host')).toBe(true);
    expect(component.form.contains('port')).toBe(true);
    expect(component.form.contains('enabled')).toBe(true);
  });

  it('applies required validator when field is required', () => {
    setup(SCHEMA);
    expect(component.form.get('host')?.validator).toBeTruthy();
    expect(component.form.get('host')?.invalid).toBe(true); // empty + required
    expect(component.form.get('enabled')?.invalid).toBe(false); // not required, boolean false is valid
  });

  it('uses default values when no existingConfig', () => {
    setup(SCHEMA);
    expect(component.form.get('host')?.value).toBe('');
    expect(component.form.get('port')?.value).toBe(0);
    expect(component.form.get('enabled')?.value).toBe(false);
  });

  it('populates controls from existingConfig', () => {
    setup(SCHEMA, { host: 'server.example.com', port: 2222, enabled: true });
    expect(component.form.get('host')?.value).toBe('server.example.com');
    expect(component.form.get('port')?.value).toBe(2222);
    expect(component.form.get('enabled')?.value).toBe(true);
  });

  it('converts array values to comma-separated string for LIST_STRING', () => {
    const listSchema: StepConfigSchema = {
      stepType: 'TEST', displayName: 'Test',
      fields: [{ name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false }],
    };
    setup(listSchema, { tags: ['alpha', 'beta'] });
    expect(component.form.get('tags')?.value).toBe('alpha, beta');
  });

  it('coerces null existingConfig values to type-appropriate defaults', () => {
    const schemaWithDefaults: StepConfigSchema = {
      stepType: 'TEST', displayName: 'Test',
      fields: [
        { name: 's', label: 'S', type: 'STRING', required: false },
        { name: 'n', label: 'N', type: 'NUMBER', required: false },
        { name: 'b', label: 'B', type: 'BOOLEAN', required: false },
      ],
    };
    setup(schemaWithDefaults, { s: null, n: null, b: null });
    expect(component.form.get('s')?.value).toBe('');
    expect(component.form.get('n')?.value).toBe(0);
    expect(component.form.get('b')?.value).toBe(false);
  });

  // --- Schema change detection (ngOnChanges) ---

  it('rebuilds form when schema changes', () => {
    setup(SCHEMA, { host: 'old.com', port: 22, enabled: false });
    expect(component.form.get('host')?.value).toBe('old.com');

    const newSchema: StepConfigSchema = {
      stepType: 'JAVA_EXEC', displayName: 'Java Exec',
      fields: [
        { name: 'mainClass', label: 'Main Class', type: 'STRING', required: true },
        { name: 'args', label: 'Args', type: 'LIST_STRING', required: false },
      ],
    };
    fixture.componentRef.setInput('schema', newSchema);
    fixture.detectChanges();

    expect(component.form.contains('host')).toBe(false);
    expect(component.form.contains('mainClass')).toBe(true);
    expect(component.form.contains('args')).toBe(true);
  });

  it('clears touchedFields when schema changes', () => {
    setup(SCHEMA);
    component.touchedFields.add('host');
    expect(component.touchedFields.size).toBe(1);

    const newSchema: StepConfigSchema = {
      stepType: 'OTHER', displayName: 'Other',
      fields: [{ name: 'x', label: 'X', type: 'STRING', required: false }],
    };
    fixture.componentRef.setInput('schema', newSchema);
    fixture.detectChanges();

    expect(component.touchedFields.size).toBe(0);
  });

  it('updates control values when existingConfig changes', () => {
    setup(SCHEMA, null);
    expect(component.form.get('host')?.value).toBe('');

    fixture.componentRef.setInput('existingConfig', { host: 'new.com', port: 80, enabled: true });
    fixture.detectChanges();

    expect(component.form.get('host')?.value).toBe('new.com');
    expect(component.form.get('port')?.value).toBe(80);
    expect(component.form.get('enabled')?.value).toBe(true);
  });

  // --- validate() ---

  it('marks all controls touched when validate() is called', () => {
    setup(SCHEMA);
    const result = component.validate();
    expect(result).toBe(false); // required fields are empty
    expect(component.form.get('host')?.touched).toBe(true);
    expect(component.form.get('port')?.touched).toBe(true);
  });

  it('returns true when all required fields have values', () => {
    setup(SCHEMA, { host: 'server.com', port: 22, enabled: false });
    const result = component.validate();
    expect(result).toBe(true);
  });

  it('populates touchedFields with all field names after validate()', () => {
    setup(SCHEMA);
    component.validate();
    expect(component.touchedFields.has('host')).toBe(true);
    expect(component.touchedFields.has('port')).toBe(true);
    expect(component.touchedFields.has('enabled')).toBe(true);
  });

  // --- toConfig() ---

  it('returns config object and validity', () => {
    setup(SCHEMA, { host: 'server.com', port: 22, enabled: true });
    const result = component.toConfig();
    expect(result.valid).toBe(true);
    expect(result.config['host']).toBe('server.com');
    expect(result.config['port']).toBe(22);
  });

  it('converts LIST_STRING from comma-separated string to array in config', () => {
    const listSchema: StepConfigSchema = {
      stepType: 'TEST', displayName: 'Test',
      fields: [{ name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false }],
    };
    setup(listSchema);
    component.form.get('tags')!.setValue('a, b, c');
    const result = component.toConfig();
    expect(result.config['tags']).toEqual(['a', 'b', 'c']);
  });

  it('returns valid=false when required fields are empty', () => {
    setup(SCHEMA); // no config → required fields are empty
    const result = component.toConfig();
    expect(result.valid).toBe(false);
  });

  it('handles empty LIST_STRING as empty array', () => {
    const listSchema: StepConfigSchema = {
      stepType: 'TEST', displayName: 'Test',
      fields: [{ name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false }],
    };
    setup(listSchema);
    component.form.get('tags')!.setValue('');
    const result = component.toConfig();
    expect(result.config['tags']).toEqual([]);
  });

  // --- Credentials passthrough ---

  it('exposes credentials array for child components', () => {
    const creds: Credential[] = [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }];
    setup(SCHEMA, null, creds);
    expect(component.credentials).toEqual(creds);
  });

  // --- Touched field tracking ---

  it('tracks touched fields via blur events from children', () => {
    setup(SCHEMA);
    expect(component.touchedFields.size).toBe(0);

    // Simulate a child field emitting blur for 'host'
    component.touchedFields.add('host');
    expect(component.touchedFields.has('host')).toBe(true);
    expect(component.touchedFields.has('port')).toBe(false);
  });
});
