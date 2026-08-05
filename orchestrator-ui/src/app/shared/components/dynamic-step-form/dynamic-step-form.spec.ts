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
    fixture.componentRef.setInput('schema', schema);
    fixture.componentRef.setInput('existingConfig', config ?? null);
    fixture.componentRef.setInput('credentials', credentials ?? []);
    fixture.detectChanges(); // renders child DynamicConfigFormComponent which builds the form
  }

  // --- Input forwarding ---

  it('forwards schema fields to child component', () => {
    setup(SCHEMA);
    const child = fixture.nativeElement.querySelector('app-dynamic-config-form');
    expect(child).toBeTruthy();
  });

  it('exposes fields getter from schema', () => {
    setup(SCHEMA);
    expect(component.fields).toEqual(FIELDS);
  });

  it('returns empty array when schema is undefined', () => {
    fixture.componentRef.setInput('schema', undefined as any);
    fixture.detectChanges();
    expect(component.fields).toEqual([]);
  });

  // --- Form ready callback ---

  it('sets form reference on formReady event', () => {
    setup(SCHEMA, { host: 'server.com', port: 22, enabled: true });
    expect(component.form).toBeTruthy();
    expect(component.form.contains('host')).toBe(true);
    expect(component.form.contains('port')).toBe(true);
    expect(component.form.contains('enabled')).toBe(true);
  });

  it('populates form from existingConfig via child', () => {
    setup(SCHEMA, { host: 'server.example.com', port: 2222, enabled: true });
    expect(component.form.get('host')?.value).toBe('server.example.com');
    expect(component.form.get('port')?.value).toBe(2222);
    expect(component.form.get('enabled')?.value).toBe(true);
  });

  // --- validate() delegation ---

  it('delegates validate() to child and returns result', () => {
    setup(SCHEMA, { host: 'server.com', port: 22, enabled: false });
    const result = component.validate();
    expect(result).toBe(true);
  });

  it('validate() returns false when required fields are empty', () => {
    setup(SCHEMA);
    const result = component.validate();
    expect(result).toBe(false);
  });

  // --- toConfig() delegation ---

  it('delegates toConfig() to child and returns config', () => {
    setup(SCHEMA, { host: 'server.com', port: 22, enabled: true });
    const result = component.toConfig();
    expect(result.valid).toBe(true);
    expect(result.config['host']).toBe('server.com');
    expect(result.config['port']).toBe(22);
  });

  it('toConfig() converts LIST_STRING to array', () => {
    const listSchema: StepConfigSchema = {
      stepType: 'TEST', displayName: 'Test',
      fields: [{ name: 'tags', label: 'Tags', type: 'LIST_STRING', required: false }],
    };
    setup(listSchema);
    component.form.get('tags')!.setValue('a, b, c');
    const result = component.toConfig();
    expect(result.config['tags']).toEqual(['a', 'b', 'c']);
  });

  // --- Credentials passthrough ---

  it('exposes credentials array for child components', () => {
    const creds: Credential[] = [{ id: 1, ref: 'db-pass', type: 'PASSWORD' }];
    setup(SCHEMA, null, creds);
    expect(component.credentials).toEqual(creds);
  });

  // --- Schema change handling via child ---

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

  it('updates control values when existingConfig changes', () => {
    setup(SCHEMA, null);
    expect(component.form.get('host')?.value).toBe('');

    fixture.componentRef.setInput('existingConfig', { host: 'new.com', port: 80, enabled: true });
    fixture.detectChanges();

    expect(component.form.get('host')?.value).toBe('new.com');
    expect(component.form.get('port')?.value).toBe(80);
    expect(component.form.get('enabled')?.value).toBe(true);
  });
});
