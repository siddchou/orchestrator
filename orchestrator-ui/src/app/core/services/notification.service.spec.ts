import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NotificationService],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('listChannelSchemas calls GET /api/notifications/channels', () => {
    service.listChannelSchemas().subscribe(res => {
      expect(res.status).toBe('SUCCESS');
      expect(res.data.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/notifications/channels');
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'SUCCESS', data: [{ type: 'EMAIL', fields: [] }] });
  });

  it('getSubscriptionsForJob(5) calls GET /api/notifications/subscriptions/job/5', () => {
    service.getSubscriptionsForJob(5).subscribe(res => {
      expect(res.data.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/notifications/subscriptions/job/5');
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'SUCCESS', data: [{ id: 1, jobId: 5 }] });
  });

  it('createSubscription POSTs to /api/notifications/subscriptions', () => {
    const request = { jobId: 5, channelType: 'EMAIL', events: ['SUCCESS'], config: {} };
    service.createSubscription(request).subscribe();

    const req = httpMock.expectOne('/api/notifications/subscriptions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ status: 'SUCCESS', data: { id: 1, ...request } });
  });

  it('updateSubscription(3, req) PUTs to /api/notifications/subscriptions/3', () => {
    const request = { jobId: 5, channelType: 'EMAIL', events: ['FAILED'], config: {} };
    service.updateSubscription(3, request).subscribe();

    const req = httpMock.expectOne('/api/notifications/subscriptions/3');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ status: 'SUCCESS', data: { id: 3, ...request } });
  });

  it('deleteSubscription(3) DELETEs /api/notifications/subscriptions/3', () => {
    service.deleteSubscription(3).subscribe();

    const req = httpMock.expectOne('/api/notifications/subscriptions/3');
    expect(req.request.method).toBe('DELETE');
    req.flush({ status: 'SUCCESS' });
  });

  it('toggleSubscription(3) PATCHes /api/notifications/subscriptions/3/toggle', () => {
    service.toggleSubscription(3).subscribe();

    const req = httpMock.expectOne('/api/notifications/subscriptions/3/toggle');
    expect(req.request.method).toBe('PATCH');
    req.flush({ status: 'SUCCESS', data: { id: 3, active: false } });
  });

  it('getDeliveryLog includes query params', () => {
    service.getDeliveryLog(10, 7).subscribe(res => {
      expect(res.data.length).toBe(0);
    });

    const req = httpMock.expectOne(r =>
      r.url.includes('/api/notifications/delivery-log') &&
      r.params.has('subscriptionId') &&
      r.params.get('subscriptionId') === '10' &&
      r.params.get('runId') === '7'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'SUCCESS', data: [] });
  });

  it('getDeliveryLog omits runId when undefined', () => {
    service.getDeliveryLog(10, undefined).subscribe();

    const req = httpMock.expectOne(r =>
      r.url.includes('/api/notifications/delivery-log') &&
      r.params.get('subscriptionId') === '10' &&
      !r.params.has('runId')
    );
    req.flush({ status: 'SUCCESS', data: [] });
  });
});
