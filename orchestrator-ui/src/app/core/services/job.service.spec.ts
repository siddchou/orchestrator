import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { JobService } from './job.service';
import { ApiResponse } from '../models/api-response.model';
import { StepDependency, EdgeCondition, JobVersionSummary, JobImportRequest } from '../models/job.model';

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

describe('JobService - Export / Import', () => {
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

  describe('exportJob', () => {
    it('exports as JSON by default', () => {
      const blob = new Blob(['{"jobName":"test"}'], { type: 'application/json' });

      let result: Blob | undefined;
      service.exportJob(42).subscribe(r => { result = r; });

      const req = httpMock.expectOne('/api/jobs/42/export?format=json');
      expect(req.request.method).toBe('GET');
      req.flush(blob);

      expect(result).toBe(blob);
    });

    it('exports as YAML when specified', () => {
      const blob = new Blob(['jobName: test'], { type: 'text/yaml' });

      service.exportJob(42, 'yaml').subscribe();

      const req = httpMock.expectOne('/api/jobs/42/export?format=yaml');
      expect(req.request.method).toBe('GET');
      req.flush(blob);
    });
  });

  describe('importJob', () => {
    it('sends POST with import request body', () => {
      const importReq: JobImportRequest = { format: 'json', content: '{"jobName":"imported"}' };
      const response: ApiResponse<any> = { status: 'SUCCESS', data: { jobId: 99 }, error: null, timestamp: '' };

      service.importJob(importReq).subscribe();

      const req = httpMock.expectOne('/api/jobs/import');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(importReq);
      req.flush(response);
    });
  });
});

describe('JobService - Version History', () => {
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

  describe('listVersions', () => {
    it('returns version summaries for a job', () => {
      const versions: JobVersionSummary[] = [
        { versionId: 1, versionNumber: 1, versionLabel: 'v1', createdAt: '2026-07-30T10:00:00Z', createdBy: 'admin' },
        { versionId: 2, versionNumber: 2, versionLabel: 'v2', createdAt: '2026-07-31T14:00:00Z', createdBy: 'admin' },
      ];
      const response: ApiResponse<JobVersionSummary[]> = { status: 'SUCCESS', data: versions, error: null, timestamp: '' };

      let result: JobVersionSummary[] | undefined;
      service.listVersions(42).subscribe(r => { result = r.data; });

      const req = httpMock.expectOne('/api/jobs/42/versions');
      expect(req.request.method).toBe('GET');
      req.flush(response);

      expect(result?.length).toBe(2);
      expect(result?.[0].versionNumber).toBe(1);
    });
  });

  describe('getVersionSnapshot', () => {
    it('fetches a specific version snapshot', () => {
      const snapshot = { jobName: 'test', steps: [] };
      const response: ApiResponse<any> = { status: 'SUCCESS', data: snapshot, error: null, timestamp: '' };

      let result: any;
      service.getVersionSnapshot(42, 5).subscribe(r => { result = r.data; });

      const req = httpMock.expectOne('/api/jobs/42/versions/5');
      expect(req.request.method).toBe('GET');
      req.flush(response);

      expect(result.jobName).toBe('test');
    });
  });

  describe('rollbackToVersion', () => {
    it('sends POST to rollback endpoint', () => {
      const response: ApiResponse<any> = { status: 'SUCCESS', data: { jobId: 42 }, error: null, timestamp: '' };

      service.rollbackToVersion(42, 3).subscribe();

      const req = httpMock.expectOne('/api/jobs/42/versions/3/rollback');
      expect(req.request.method).toBe('POST');
      req.flush(response);
    });
  });
});
