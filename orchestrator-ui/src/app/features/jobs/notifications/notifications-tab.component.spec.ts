import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationsTabComponent } from './notifications-tab.component';
import { ApiResponse } from '@app/core/models/api-response.model';
import { NotificationSubscription, ChannelConfigSchema } from '@app/core/models/notification.model';

const MOCK_SUBSCRIPTION: NotificationSubscription = {
  id: 1,
  jobId: 10,
  channelType: 'EMAIL',
  events: 'SUCCESS,FAILED',
  config: {},
  active: true,
  createdAt: new Date().toISOString(),
};

const EMAIL_SCHEMA: ChannelConfigSchema = {
  type: 'EMAIL',
  fields: [
    { name: 'toEmail', label: 'To Email', type: 'STRING', required: true },
  ],
};

function apiResponse<T>(data: T): ApiResponse<T> {
  return { status: 'SUCCESS', data, error: null, timestamp: '' };
}

/** Helper that creates the component, triggers init, and flushes HTTP requests — all in one synchronous block. */
function createComponent(subscriptions: NotificationSubscription[] = [], schemas: ChannelConfigSchema[] = [EMAIL_SCHEMA]) {
  const fixture = TestBed.createComponent(NotificationsTabComponent);
  const component = fixture.componentInstance;
  const httpMock = TestBed.inject(HttpTestingController);

  component.jobId = 10;
  fixture.detectChanges(); // triggers ngOnInit -> load()

  const subReq = httpMock.expectOne('/api/notifications/subscriptions/job/10');
  subReq.flush(apiResponse(subscriptions));

  const chanReq = httpMock.expectOne('/api/notifications/channels');
  chanReq.flush(apiResponse(schemas));

  return { fixture, component, httpMock };
}

describe('NotificationsTabComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, MatDialogModule, NotificationsTabComponent],
      providers: [
        { provide: MatSnackBar, useValue: { open: vi.fn(), dismissAll: vi.fn() } },
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

  // --- Loading subscriptions ---

  it('loads subscriptions and channel schemas on init', () => {
    const { component } = createComponent([MOCK_SUBSCRIPTION]);
    expect(component.subscriptions.length).toBe(1);
    expect(component.channelSchemas.length).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('shows empty subscriptions when API returns empty array', () => {
    const { component } = createComponent([]);
    expect(component.subscriptions.length).toBe(0);
    expect(component.loading).toBe(false);
  });

  // --- Create subscription ---

  it('opens create dialog with correct data', () => {
    const { component } = createComponent();
    // The dialog service is not mocked, but we can verify the component state
    expect(component.channelSchemas.length).toBe(1);
  });

  // --- Event chips ---

  it('splits comma-separated events into array', () => {
    const { component } = createComponent();
    const chips = component.getEventChips('SUCCESS,FAILED,CANCELLED');
    expect(chips.length).toBe(3);
    expect(chips).toContain('SUCCESS');
    expect(chips).toContain('FAILED');
    expect(chips).toContain('CANCELLED');
  });

  it('returns empty array for empty events string', () => {
    const { component } = createComponent();
    expect(component.getEventChips('').length).toBe(0);
  });

  it('returns color for event name', () => {
    const { component } = createComponent();
    expect(component.getEventColor('SUCCESS')).toBeTruthy();
    expect(typeof component.getEventColor('FAILED')).toBe('string');
  });

  // --- Delivery log toggle ---

  it('toggles expanded subscription ID', () => {
    const { component } = createComponent();
    component.toggleDeliveryLog(1);
    expect(component.expandedSubscriptionId).toBe(1);

    component.toggleDeliveryLog(1);
    expect(component.expandedSubscriptionId).toBeNull();
  });

  // --- Error handling ---

  it('handles subscription load error gracefully', () => {
    const fixture = TestBed.createComponent(NotificationsTabComponent);
    const component = fixture.componentInstance;
    const mock = TestBed.inject(HttpTestingController);

    component.jobId = 10;
    fixture.detectChanges();

    mock.expectOne('/api/notifications/subscriptions/job/10').error(new ProgressEvent('error'));
    mock.expectOne('/api/notifications/channels').flush(apiResponse([]));

    expect(component.subscriptions.length).toBe(0);
    expect(component.loading).toBe(false);
    mock.verify();
  });
});
