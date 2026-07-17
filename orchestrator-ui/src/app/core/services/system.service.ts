import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '@app/core/models/api-response.model';
import { EnvVar } from '@app/core/models/job.model';
import { HealthStatus, EnvValidationResult, CronValidationResult } from '@app/core/models/system.model';

@Injectable({ providedIn: 'root' })
export class SystemService {
  private http = inject(HttpClient);
  private api = '/api';

  getGlobalEnvVars(): Observable<ApiResponse<EnvVar[]>> {
    return this.http.get<ApiResponse<EnvVar[]>>(`${this.api}/env-vars/global`);
  }

  addGlobalEnvVar(env: { key: string; value: string }): Observable<ApiResponse<EnvVar>> {
    return this.http.post<ApiResponse<EnvVar>>(`${this.api}/env-vars/global`, env);
  }

  deleteGlobalEnvVar(envId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/env-vars/global/${envId}`);
  }

  getHealth(): Observable<ApiResponse<HealthStatus>> {
    return this.http.get<ApiResponse<HealthStatus>>(`${this.api}/system/health`);
  }

  validateEnv(javaHome: string, workingDir: string): Observable<ApiResponse<EnvValidationResult>> {
    return this.http.get<ApiResponse<EnvValidationResult>>(`${this.api}/system/env-validate`, {
      params: { javaHome, workingDir },
    });
  }

  validateCron(cronExpression: string): Observable<ApiResponse<CronValidationResult>> {
    return this.http.get<ApiResponse<CronValidationResult>>(`${this.api}/system/cron-validate`, {
      params: { cronExpression },
    });
  }
}
