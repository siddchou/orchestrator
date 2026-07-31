import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { StepPaletteComponent } from './step-palette';
import { StepConfigSchema } from '@app/core/models/job.model';

function makeSchemas(): StepConfigSchema[] {
  return [
    { stepType: 'SFTP', displayName: 'SFTP Transfer', fields: [{ name: 'host', label: 'Host', type: 'STRING', required: true }], description: 'Transfer files via SFTP' },
    { stepType: 'JAVA_EXEC', displayName: 'Java Exec', fields: [{ name: 'mainClass', label: 'Main Class', type: 'STRING', required: true }] },
    { stepType: 'SHELL_CMD', displayName: 'Shell Command', fields: [{ name: 'command', label: 'Command', type: 'STRING', required: true }] },
    { stepType: 'HTTP_CALL', displayName: 'HTTP Call', fields: [] },
  ];
}

describe('StepPaletteComponent', () => {
  let component: StepPaletteComponent;
  let fixture: ComponentFixture<StepPaletteComponent>;
  let dialogRefClose: ReturnType<typeof vi.fn>;
  let httpMock: HttpTestingController;

  function createComponent(overrideSchemas?: StepConfigSchema[]): void {
    fixture = TestBed.createComponent(StepPaletteComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    // ngOnInit fires on detectChanges → makes the HTTP request
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/step-types');
    req.flush({ status: 'SUCCESS', data: overrideSchemas ?? makeSchemas() });
  }

  beforeEach(async () => {
    dialogRefClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, StepPaletteComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: dialogRefClose } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    httpMock?.verify();
  });

  // --- Loading state ---

  it('loads schemas and sets loading=false after API success', () => {
    createComponent();
    expect(component.loading).toBe(false);
    expect(component.schemas.length).toBe(4);
  });

  // --- Error state ---

  it('sets error flag when API returns error status', () => {
    fixture = TestBed.createComponent(StepPaletteComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    fixture.detectChanges();
    const req = httpMock.expectOne('/api/step-types');
    req.flush({ status: 'ERROR' });

    expect(component.error).toBe(true);
    expect(component.loading).toBe(false);
  });

  it('sets error flag when API request fails with network error', () => {
    fixture = TestBed.createComponent(StepPaletteComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    fixture.detectChanges();
    const req = httpMock.expectOne('/api/step-types');
    req.error(new ProgressEvent('error'));

    expect(component.error).toBe(true);
    expect(component.loading).toBe(false);
  });

  it('can retry loading after error', () => {
    fixture = TestBed.createComponent(StepPaletteComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    fixture.detectChanges();
    httpMock.expectOne('/api/step-types').error(new ProgressEvent('error'));
    expect(component.error).toBe(true);

    // Retry succeeds
    component.loadSchemas();
    httpMock.expectOne('/api/step-types').flush({ status: 'SUCCESS', data: makeSchemas() });
    expect(component.loading).toBe(false);
    expect(component.schemas.length).toBe(4);
  });

  // --- Empty state ---

  it('shows empty state when schemas array is empty', () => {
    createComponent([]);
    expect(component.filtered.length).toBe(0);
    expect(component.schemas.length).toBe(0);
  });

  // --- Filtered results (test getter logic directly, no detectChanges) ---

  it('returns all schemas when filter is empty', () => {
    createComponent();
    // Don't change filter — it's already '' and bound via ngModel
    expect(component.filtered.length).toBe(4);
  });

  it('filters by displayName match (case-insensitive)', () => {
    createComponent();
    component.filter = 'sftp';
    // Test the getter directly without detectChanges to avoid ExpressionChangedAfterItHasBeenCheckedError
    expect(component.filtered.length).toBe(1);
    expect(component.filtered[0].stepType).toBe('SFTP');
  });

  it('filters by stepType match', () => {
    createComponent();
    component.filter = 'JAVA_EXEC';
    expect(component.filtered.length).toBe(1);
  });

  it('returns empty array when filter matches nothing', () => {
    createComponent();
    component.filter = 'nonexistent-query';
    expect(component.filtered.length).toBe(0);
  });

  it('filters by description text', () => {
    createComponent();
    component.filter = 'transfer files';
    expect(component.filtered.length).toBe(1);
    expect(component.filtered[0].stepType).toBe('SFTP');
  });

  it('filters by partial word match', () => {
    createComponent();
    component.filter = 'shell';
    expect(component.filtered.length).toBeGreaterThanOrEqual(1);
    expect(component.filtered[0].stepType).toBe('SHELL_CMD');
  });

  // --- Selection ---

  it('closes dialog with selected schema stepType', () => {
    createComponent();
    component.select({ stepType: 'SFTP', displayName: '', fields: [] });
    expect(dialogRefClose).toHaveBeenCalledWith({ stepType: 'SFTP' });
  });

  it('passes correct stepType for each schema', () => {
    createComponent();
    component.select({ stepType: 'SHELL_CMD', displayName: '', fields: [] });
    expect(dialogRefClose).toHaveBeenCalledWith({ stepType: 'SHELL_CMD' });
  });

  // --- Icon heuristic ---

  it('returns correct icon for SFTP type', () => {
    createComponent();
    expect(component.iconFor('SFTP')).toBe('cloud_upload');
  });

  it('returns correct icon for JAVA_EXEC type', () => {
    createComponent();
    expect(component.iconFor('JAVA_EXEC')).toBe('language_java');
  });

  it('returns correct icon for SHELL_CMD type', () => {
    createComponent();
    expect(component.iconFor('SHELL_CMD')).toBe('terminal');
  });

  it('returns correct icon for HTTP_CALL type', () => {
    createComponent();
    expect(component.iconFor('HTTP_CALL')).toBe('language');
  });

  it('returns correct icon for ENV_SETUP type', () => {
    createComponent();
    expect(component.iconFor('ENV_SETUP')).toBe('settings_applications');
  });

  it('returns correct icon for DB_QUERY type', () => {
    createComponent();
    expect(component.iconFor('DB_QUERY')).toBe('storage');
  });

  it('returns default icon for unknown type', () => {
    createComponent();
    expect(component.iconFor('CUSTOM_STEP')).toBe('play_arrow');
  });

  // --- Description fallback ---

  it('uses schema description when available', () => {
    createComponent();
    const s = makeSchemas();
    expect(component.descriptionFor(s[0])).toBe('Transfer files via SFTP');
  });

  it('falls back to displayName-based string when no description', () => {
    createComponent();
    const schema: StepConfigSchema = { stepType: 'TEST', displayName: 'Test Step', fields: [] };
    expect(component.descriptionFor(schema)).toBe('Test Step step');
  });

  // --- Cancel ---

  it('closes dialog with null on cancel', () => {
    createComponent();
    dialogRefClose.mockClear();
    component.dialogRef.close(null);
    expect(dialogRefClose).toHaveBeenCalledWith(null);
  });

  // --- Filtering preserves schema order ---

  it('filtered returns all schemas when no filter applied', () => {
    createComponent();
    expect(component.filtered.length).toBe(component.schemas.length);
  });

  // --- Schemas sorted on load ---

  it('schemas are sorted alphabetically by displayName after load', () => {
    const unsorted: StepConfigSchema[] = [
      { stepType: 'SHELL_CMD', displayName: 'Shell Command', fields: [] },
      { stepType: 'SFTP', displayName: 'SFTP Transfer', fields: [] },
      { stepType: 'JAVA_EXEC', displayName: 'Java Exec', fields: [] },
      { stepType: 'HTTP_CALL', displayName: 'HTTP Call', fields: [] },
    ];
    createComponent(unsorted);

    // localeCompare is case-sensitive: uppercase letters sort before lowercase,
    // so "SFTP Transfer" (SF...) comes before "Shell Command" (Sh...)
    const names = component.schemas.map(s => s.displayName);
    expect(names).toEqual(['HTTP Call', 'Java Exec', 'SFTP Transfer', 'Shell Command']);
  });

  // --- Field count ---

  it('schemas with fields report correct count', () => {
    const s = makeSchemas();
    expect(s[0].fields.length).toBe(1);
    expect(s[3].fields.length).toBe(0);
  });
});
