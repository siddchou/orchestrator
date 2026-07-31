import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { JobService } from './job.service';
import { ApiResponse } from '../models/api-response.model';
import { StepDependency, EdgeCondition } from '../models/job.model';

describe('JobService - Step Dependencies', () => {
  let service: JobService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [JobService],
    });
    service = TestBed.inject(JobService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getStepDependencies', () => {
    it('sends GET to correct URL with jobId and stepId', () => {
      const deps: StepDependency[] = [
        { dependsOnStepId: 1, dependsOnStepName: 'Build', edgeCondition: 'ON_SUCCESS' },
      ];
      const response: ApiResponse<StepDependency[]> = { status: 'SUCCESS', data: deps, error: null, timestamp: '' };

      let result: StepDependency[] | undefined;
      service.getStepDependencies(42, 7).subscribe(r => { result = r.data; });

      const req = httpMock.expectOne('/api/jobs/42/steps/7/dependencies');
      expect(req.request.method).toBe('GET');
      req.flush(response);

      expect(result?.length).toBe(1);
      expect(result?.[0].dependsOnStepId).toBe(1);
      expect(result?.[0].edgeCondition).toBe('ON_SUCCESS');
    });

    it('returns empty array when no dependencies', () => {
      const response: ApiResponse<StepDependency[]> = { status: 'SUCCESS', data: [], error: null, timestamp: '' };

      let result: StepDependency[] | undefined;
      service.getStepDependencies(10, 3).subscribe(r => { result = r.data; });

      const req = httpMock.expectOne('/api/jobs/10/steps/3/dependencies');
      req.flush(response);

      expect(result?.length).toBe(0);
    });
  });

  describe('setStepDependencies', () => {
    it('sends PUT with dependencies array to correct URL', () => {
      const deps: StepDependency[] = [
        { dependsOnStepId: 2, dependsOnStepName: 'Test', edgeCondition: 'ON_SUCCESS' },
        { dependsOnStepId: 3, dependsOnStepName: 'Lint', edgeCondition: 'ALWAYS' },
      ];

      service.setStepDependencies(5, 8, deps).subscribe();

      const req = httpMock.expectOne('/api/jobs/5/steps/8/dependencies');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(deps);
      req.flush({ status: 'SUCCESS', data: undefined, error: null, timestamp: '' });
    });

    it('sends empty array to clear all dependencies', () => {
      service.setStepDependencies(1, 2, []).subscribe();

      const req = httpMock.expectOne('/api/jobs/1/steps/2/dependencies');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual([]);
      req.flush({ status: 'SUCCESS', data: undefined, error: null, timestamp: '' });
    });

    it('supports all edge conditions', () => {
      const conditions: EdgeCondition[] = ['ON_SUCCESS', 'ON_FAILURE', 'ALWAYS'];
      const deps: StepDependency[] = conditions.map((c, i) => ({
        dependsOnStepId: i + 1,
        dependsOnStepName: `Step ${i + 1}`,
        edgeCondition: c,
      }));

      service.setStepDependencies(99, 1, deps).subscribe();

      const req = httpMock.expectOne('/api/jobs/99/steps/1/dependencies');
      expect(req.request.body[0].edgeCondition).toBe('ON_SUCCESS');
      expect(req.request.body[1].edgeCondition).toBe('ON_FAILURE');
      expect(req.request.body[2].edgeCondition).toBe('ALWAYS');
      req.flush({ status: 'SUCCESS', data: undefined, error: null, timestamp: '' });
    });
  });
});
