import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, Page } from '../models/api-response.model';
import { JobRunSummary, JobRunDetail } from '../models/run.model';

@Injectable({ providedIn: 'root' })
export class RunService {
  private http = inject(HttpClient);
  private api = '/api';

  listRuns(
    page = 0, size = 20, jobId?: number, status?: string,
    startDate?: string, endDate?: string
  ): Observable<ApiResponse<Page<JobRunSummary>>> {
    const params: any = { page, size };
    if (jobId) params.jobId = jobId;
    if (status) params.status = status;
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    return this.http.get<ApiResponse<Page<JobRunSummary>>>(`${this.api}/runs`, { params });
  }

  getRunDetail(runId: number): Observable<ApiResponse<JobRunDetail>> {
    return this.http.get<ApiResponse<JobRunDetail>>(`${this.api}/runs/${runId}`);
  }

  getStepLog(runId: number, stepId: number): Observable<ApiResponse<string>> {
    return this.http.get<ApiResponse<string>>(`${this.api}/runs/${runId}/steps/${stepId}/log`);
  }

  cancelRun(runId: number): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.api}/runs/${runId}/cancel`, {});
  }
}
