import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Page } from '@app/core/models/api-response.model';
import { JobDefinition, JobStep, EnvVar, JobSchedule, StepConfigSchema, StepDependency, JobVersionSummary, JobImportRequest } from '@app/core/models/job.model';

@Injectable({ providedIn: 'root' })
export class JobService {
  private http = inject(HttpClient);
  private api = '/api';

  listJobs(page = 0, size = 20, search = ''): Observable<ApiResponse<Page<JobDefinition>>> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<ApiResponse<Page<JobDefinition>>>(`${this.api}/jobs`, { params });
  }

  getJob(id: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.get<ApiResponse<JobDefinition>>(`${this.api}/jobs/${id}`);
  }

  createJob(body: { jobName: string; description?: string; workingDir: string; javaHome?: string; classpathEntries?: string[] }): Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(`${this.api}/jobs`, body);
  }

  updateJob(id: number, body: Partial<JobDefinition>): Observable<ApiResponse<JobDefinition>> {
    return this.http.put<ApiResponse<JobDefinition>>(`${this.api}/jobs/${id}`, body);
  }

  deleteJob(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/jobs/${id}`);
  }

  toggleEnabled(id: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(`${this.api}/jobs/${id}/enable`, {});
  }

  triggerRun(id: number): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.api}/jobs/${id}/run`, {});
  }

  // Steps
  addStep(jobId: number, step: {
    stepName: string; stepOrder: number; stepType: string; stepConfig: string;
    continueOnFailure: boolean; enabled: boolean;
  }): Observable<ApiResponse<JobStep>> {
    return this.http.post<ApiResponse<JobStep>>(`${this.api}/jobs/${jobId}/steps`, step);
  }

  updateStep(jobId: number, stepId: number, step: Partial<JobStep>): Observable<ApiResponse<JobStep>> {
    return this.http.put<ApiResponse<JobStep>>(`${this.api}/jobs/${jobId}/steps/${stepId}`, step);
  }

  deleteStep(jobId: number, stepId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/jobs/${jobId}/steps/${stepId}`);
  }

  reorderSteps(jobId: number, stepIds: number[]): Observable<ApiResponse<JobStep[]>> {
    return this.http.put<ApiResponse<JobStep[]>>(`${this.api}/jobs/${jobId}/steps/reorder`, { stepIds });
  }

  // Env Vars
  getEnvVars(jobId: number): Observable<ApiResponse<EnvVar[]>> {
    return this.http.get<ApiResponse<EnvVar[]>>(`${this.api}/jobs/${jobId}/env-vars`);
  }

  addEnvVar(jobId: number, env: { key: string; value: string }): Observable<ApiResponse<EnvVar>> {
    return this.http.post<ApiResponse<EnvVar>>(`${this.api}/jobs/${jobId}/env-vars`, env);
  }

  deleteEnvVar(jobId: number, envId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/jobs/${jobId}/env-vars/${envId}`);
  }

  // Schedule
  getSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.get<ApiResponse<JobSchedule>>(`${this.api}/jobs/${jobId}/schedule`);
  }

  createSchedule(jobId: number, cronExpression: string): Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(`${this.api}/jobs/${jobId}/schedule`, { cronExpression });
  }

  updateSchedule(jobId: number, cronExpression: string): Observable<ApiResponse<JobSchedule>> {
    return this.http.put<ApiResponse<JobSchedule>>(`${this.api}/jobs/${jobId}/schedule`, { cronExpression });
  }

  deleteSchedule(jobId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/jobs/${jobId}/schedule`);
  }

  enableSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(`${this.api}/jobs/${jobId}/schedule/enable`, {});
  }

  disableSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(`${this.api}/jobs/${jobId}/schedule/disable`, {});
  }

  // Step Types (schema-driven UI)
  listStepTypes(): Observable<ApiResponse<StepConfigSchema[]>> {
    return this.http.get<ApiResponse<StepConfigSchema[]>>(`${this.api}/step-types`);
  }

  // Step Dependencies (DAG)
  getStepDependencies(jobId: number, stepId: number): Observable<ApiResponse<StepDependency[]>> {
    return this.http.get<ApiResponse<StepDependency[]>>(`${this.api}/jobs/${jobId}/steps/${stepId}/dependencies`);
  }

  setStepDependencies(jobId: number, stepId: number, dependencies: StepDependency[]): Observable<ApiResponse<void>> {
    return this.http.put<ApiResponse<void>>(`${this.api}/jobs/${jobId}/steps/${stepId}/dependencies`, dependencies);
  }

  // Export / Import
  exportJob(id: number, format: 'json' | 'yaml' = 'json'): Observable<Blob> {
    return this.http.get(`${this.api}/jobs/${id}/export`, {
      params: { format },
      responseType: 'blob',
    });
  }

  importJob(body: JobImportRequest): Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(`${this.api}/jobs/import`, body);
  }

  // Version History
  listVersions(jobId: number): Observable<ApiResponse<JobVersionSummary[]>> {
    return this.http.get<ApiResponse<JobVersionSummary[]>>(`${this.api}/jobs/${jobId}/versions`);
  }

  getVersionSnapshot(jobId: number, versionId: number): Observable<object> {
    return this.http.get<object>(`${this.api}/jobs/${jobId}/versions/${versionId}`);
  }

  rollbackToVersion(jobId: number, versionId: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(`${this.api}/jobs/${jobId}/versions/${versionId}/rollback`, {});
  }
}
