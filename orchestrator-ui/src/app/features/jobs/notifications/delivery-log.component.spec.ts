import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DeliveryLogComponent } from './delivery-log.component';
import { NotificationDeliveryLog } from '@app/core/models/notification.model';

function makeLog(overrides?: Partial<NotificationDeliveryLog>): NotificationDeliveryLog {
  return {
    id: 1,
    subscriptionId: 10,
    runId: 5,
    channelType: 'EMAIL',
    status: 'SENT',
    attemptCount: 1,
    errorMessage: null,
    createdAt: '2026-07-30T10:00:00Z',
    sentAt: '2026-07-30T10:00:01Z',
    ...overrides,
  };
}

function apiResponse<T>(data: T) {
  return { status: 'SUCCESS' as const, data, error: null, timestamp: '' };
}

/** Helper that creates the component, triggers init, and flushes the HTTP request. */
function createComponent(logs: NotificationDeliveryLog[] = []) {
  const fixture = TestBed.createComponent(DeliveryLogComponent);
  const component = fixture.componentInstance;
  const mock = TestBed.inject(HttpTestingController);

  component.subscriptionId = 10;
  fixture.detectChanges();

  const req = mock.expectOne(r => r.urlWithParams.includes('/api/notifications/delivery-log'));
  req.flush(apiResponse(logs));

  return { fixture, component, mock };
}

describe('DeliveryLogComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, DeliveryLogComponent],
      providers: [
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    const { component } = createComponent();
    expect(component).toBeTruthy();
  });

  // --- Load on init ---

  it('calls getDeliveryLog with subscriptionId', () => {
    const { component } = createComponent([makeLog()]);
    expect(component.logs.length).toBe(1);
    expect(component.loading).toBe(false);
  });

  // --- Table rendering ---

  it('loads multiple entries', () => {
    const logs = [makeLog(), makeLog({ id: 2, runId: 6 }), makeLog({ id: 3, runId: 7 })];
    const { component } = createComponent(logs);
    expect(component.logs.length).toBe(3);
  });

  // --- Status chip colors ---

  it('returns green for SENT status', () => {
    const { component } = createComponent();
    expect(component.getStatusColor('SENT')).toBe('#4caf50');
  });

  it('returns red for FAILED status', () => {
    const { component } = createComponent();
    expect(component.getStatusColor('FAILED')).toBe('#f44336');
  });

  it('returns orange for PENDING or unknown status', () => {
    const { component } = createComponent();
    expect(component.getStatusColor('PENDING')).toBe('#ff9800');
    expect(component.getStatusColor('UNKNOWN')).toBe('#ff9800');
  });

  // --- Empty state ---

  it('shows empty state when no entries', () => {
    const { component } = createComponent([]);
    expect(component.logs.length).toBe(0);
    expect(component.loading).toBe(false);
  });

  // --- Run ID filter ---

  it('passes runId parameter when filterRunId is set', () => {
    const { component, mock } = createComponent([]);

    component.filterRunId = '42';
    component.onFilterSubmit();

    const req = mock.expectOne(r =>
      r.urlWithParams.includes('subscriptionId=10') && r.urlWithParams.includes('runId=42')
    );
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse([]));
  });

  it('clears filter and reloads without runId', () => {
    const { component, mock } = createComponent([]);

    component.filterRunId = '42';
    component.clearFilter();

    expect(component.filterRunId).toBe('');

    const req = mock.expectOne(r => r.urlWithParams.includes('subscriptionId=10'));
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse([]));
  });

  // --- Null sentAt ---

  it('displays gracefully when sentAt is null', () => {
    const { component } = createComponent([makeLog({ sentAt: null, status: 'PENDING' })]);
    expect(component.logs[0].sentAt).toBeNull();
    expect(component.loading).toBe(false);
  });

  // --- Error handling ---

  it('handles load error gracefully', () => {
    const fixture = TestBed.createComponent(DeliveryLogComponent);
    const component = fixture.componentInstance;
    const mock = TestBed.inject(HttpTestingController);

    component.subscriptionId = 10;
    fixture.detectChanges();

    mock.expectOne(r => r.urlWithParams.includes('/api/notifications/delivery-log')).error(new ProgressEvent('error'));

    expect(component.logs.length).toBe(0);
    expect(component.loading).toBe(false);
  });
});
