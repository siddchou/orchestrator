# Phase 5a — UI: Project Setup, Models & Core Services

> **Goal:** Scaffold the Angular project, define all TypeScript models that mirror the
> Java DTOs, and build the core services and interceptors used by every feature module.
> No UI components yet — just the data layer and wiring.

> **Depends on:** Phase 3a DTOs (TypeScript mirrors them)  
> **Produces:** Angular project skeleton, `core/models/`, `core/services/`, `core/interceptors/`

---

## 5a.1 Project Bootstrap

```bash
# From the repo root (sibling of orchestrator-api/)
ng new orchestrator-ui --standalone --routing --style=scss
cd orchestrator-ui
ng add @angular/material    # choose any theme, e.g. Indigo/Pink
```

### `angular.json` — output into Spring Boot static resources

```json
{
  "projects": {
    "orchestrator-ui": {
      "architect": {
        "build": {
          "options": {
            "outputPath": "../orchestrator-api/src/main/resources/static",
            "index": "src/index.html",
            "main": "src/main.ts",
            "styles": ["src/styles.scss"],
            "scripts": []
          }
        }
      }
    }
  }
}
```

### `package.json` — key scripts

```json
{
  "scripts": {
    "start": "ng serve --proxy-config proxy.conf.json",
    "build": "ng build --configuration production",
    "test": "ng test"
  }
}
```

### `proxy.conf.json` — proxy API calls to Spring Boot during development

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

This means `ng serve` at `http://localhost:4200` forwards `/api/*` calls to Spring Boot
at `http://localhost:8080/api/*` — no CORS issues during development.

---

## 5a.2 Project Structure

```
src/app/
├── core/
│   ├── models/
│   │   ├── api-response.model.ts
│   │   ├── job.model.ts
│   │   └── run.model.ts
│   ├── services/
│   │   ├── auth.service.ts
│   │   ├── job.service.ts
│   │   ├── run.service.ts
│   │   └── log-stream.service.ts
│   └── interceptors/
│       ├── auth.interceptor.ts
│       └── error.interceptor.ts
│
├── features/           ← built in 5b, 5c, 5d, 5e
│
├── shared/             ← built in 5e
│
├── app.component.ts    ← shell with sidenav
├── app.component.html
├── app.component.scss
└── app.routes.ts       ← built in 5e
```

---

## 5a.3 TypeScript Models

### `api-response.model.ts`

```typescript
export interface ApiResponse<T> {
  status: 'SUCCESS' | 'ERROR';
  data: T;
  error: string | null;
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

### `job.model.ts`

```typescript
export type StepType = 'ENV_SETUP' | 'LOG_CLEANUP' | 'JAVA_EXEC' | 'SFTP' | 'ARCHIVE';

export interface JobDefinition {
  jobId: number;
  jobName: string;
  description: string | null;
  workingDir: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  steps: JobStep[];
  envVars: EnvVar[];
  schedule: JobSchedule | null;
}

export interface JobStep {
  stepId: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;        // JSON string; each feature component parses this
  continueOnFailure: boolean;
  enabled: boolean;
}

export interface EnvVar {
  envId: number;
  varName: string;
  varValue: string;
  global: boolean;
}

export interface JobSchedule {
  scheduleId: number;
  cronExpression: string;
  enabled: boolean;
  nextFireTime: string | null;
}

// Step config shapes — parsed from JobStep.stepConfig JSON
export interface EnvSetupConfig {
  javaHome: string;
  classpathEntries: string[];
  extraEnvVars: Record<string, string>;
}

export interface LogCleanupConfig {
  directory: string;
  filePattern: string;
}

export interface JavaExecConfig {
  mainClass?: string;
  jarPath?: string;
  args: string[];
  jvmArgs: string[];
  timeoutMinutes: number | null;
}

export interface SftpConfig {
  host: string;
  port: number;
  username: string;
  credentialRef: string;
  remoteDir: string;
  filePattern: string;
  direction: 'UPLOAD' | 'DOWNLOAD';
}

export interface ArchiveConfig {
  sourceDir: string;
  filePatterns: string[];
  archiveDir: string;
  archiveFormat: 'ZIP' | 'TAR_GZ';
}
```

### `run.model.ts`

```typescript
import { StepType } from './job.model';

export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED';
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'API';

export interface JobRunSummary {
  runId: number;
  jobId: number;
  jobName: string;
  status: RunStatus;
  triggerType: TriggerType;
  triggeredBy: string;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number;
}

export interface JobRunDetail extends JobRunSummary {
  steps: RunStepDetail[];
}

export interface RunStepDetail {
  runStepId: number;
  stepName: string;
  stepType: StepType;
  stepOrder: number;
  status: RunStatus;
  exitCode: number | null;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number;
}
```

---

## 5a.4 `AuthService`

Handles JWT login, token storage, and logout. Stores the token in memory (not
`localStorage`) to protect against XSS.

```typescript
// core/services/auth.service.ts

@Injectable({ providedIn: 'root' })
export class AuthService {

  private token: string | null = null;
  private readonly loginUrl = '/api/auth/login';

  constructor(private http: HttpClient, private router: Router) {}

  login(username: string, password: string): Observable<void> {
    return this.http.post<ApiResponse<{ accessToken: string; expiresInSeconds: number }>>(
      this.loginUrl, { username, password }
    ).pipe(
      tap(resp => {
        if (resp.status === 'SUCCESS' && resp.data) {
          this.token = resp.data.accessToken;
        }
      }),
      map(() => void 0)
    );
  }

  logout(): void {
    this.token = null;
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return this.token;
  }

  isLoggedIn(): boolean {
    return this.token !== null;
  }
}
```

---

## 5a.5 `JobService`

```typescript
// core/services/job.service.ts

@Injectable({ providedIn: 'root' })
export class JobService {
  private base = '/api/jobs';

  constructor(private http: HttpClient) {}

  // Job CRUD
  list(page = 0, size = 20, search = ''): Observable<ApiResponse<Page<JobDefinition>>> {
    return this.http.get<ApiResponse<Page<JobDefinition>>>(
      this.base, { params: { page, size, ...(search ? { search } : {}) } }
    );
  }

  get(id: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.get<ApiResponse<JobDefinition>>(`${this.base}/${id}`);
  }

  create(body: { jobName: string; description: string; workingDir: string })
      : Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(this.base, body);
  }

  update(id: number, body: { jobName: string; description: string; workingDir: string })
      : Observable<ApiResponse<JobDefinition>> {
    return this.http.put<ApiResponse<JobDefinition>>(`${this.base}/${id}`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  toggleEnabled(id: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.post<ApiResponse<JobDefinition>>(`${this.base}/${id}/enable`, {});
  }

  // Steps
  addStep(jobId: number, step: Partial<JobStep>): Observable<ApiResponse<JobStep>> {
    return this.http.post<ApiResponse<JobStep>>(`${this.base}/${jobId}/steps`, step);
  }

  updateStep(jobId: number, stepId: number, step: Partial<JobStep>)
      : Observable<ApiResponse<JobStep>> {
    return this.http.put<ApiResponse<JobStep>>(
      `${this.base}/${jobId}/steps/${stepId}`, step);
  }

  deleteStep(jobId: number, stepId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${jobId}/steps/${stepId}`);
  }

  reorderSteps(jobId: number, stepIds: number[]): Observable<ApiResponse<JobStep[]>> {
    return this.http.put<ApiResponse<JobStep[]>>(
      `${this.base}/${jobId}/steps/reorder`, { stepIds });
  }

  // Env Vars
  listEnvVars(jobId: number): Observable<ApiResponse<EnvVar[]>> {
    return this.http.get<ApiResponse<EnvVar[]>>(`${this.base}/${jobId}/env-vars`);
  }

  addEnvVar(jobId: number, body: { varName: string; varValue: string })
      : Observable<ApiResponse<EnvVar>> {
    return this.http.post<ApiResponse<EnvVar>>(`${this.base}/${jobId}/env-vars`, body);
  }

  deleteEnvVar(jobId: number, envId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${jobId}/env-vars/${envId}`);
  }

  // Schedule
  getSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.get<ApiResponse<JobSchedule>>(`${this.base}/${jobId}/schedule`);
  }

  createSchedule(jobId: number, cronExpression: string)
      : Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(
      `${this.base}/${jobId}/schedule`, { cronExpression });
  }

  updateSchedule(jobId: number, cronExpression: string)
      : Observable<ApiResponse<JobSchedule>> {
    return this.http.put<ApiResponse<JobSchedule>>(
      `${this.base}/${jobId}/schedule`, { cronExpression });
  }

  deleteSchedule(jobId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${jobId}/schedule`);
  }

  enableSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(
      `${this.base}/${jobId}/schedule/enable`, {});
  }

  disableSchedule(jobId: number): Observable<ApiResponse<JobSchedule>> {
    return this.http.post<ApiResponse<JobSchedule>>(
      `${this.base}/${jobId}/schedule/disable`, {});
  }

  // Trigger run
  triggerRun(jobId: number): Observable<ApiResponse<JobRunSummary>> {
    return this.http.post<ApiResponse<JobRunSummary>>(
      `${this.base}/${jobId}/run`, {});
  }
}
```

---

## 5a.6 `RunService`

```typescript
// core/services/run.service.ts

@Injectable({ providedIn: 'root' })
export class RunService {
  private base = '/api/runs';

  constructor(private http: HttpClient) {}

  list(params: {
    jobId?: number; status?: RunStatus;
    from?: string; to?: string;
    page?: number; size?: number;
  }): Observable<ApiResponse<Page<JobRunSummary>>> {
    const p: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20
    };
    if (params.jobId)  p['jobId']  = params.jobId;
    if (params.status) p['status'] = params.status;
    if (params.from)   p['from']   = params.from;
    if (params.to)     p['to']     = params.to;
    return this.http.get<ApiResponse<Page<JobRunSummary>>>(this.base, { params: p });
  }

  get(runId: number): Observable<ApiResponse<JobRunDetail>> {
    return this.http.get<ApiResponse<JobRunDetail>>(`${this.base}/${runId}`);
  }

  getStepLog(runId: number, runStepId: number): Observable<ApiResponse<string>> {
    return this.http.get<ApiResponse<string>>(
      `${this.base}/${runId}/steps/${runStepId}/log`);
  }

  cancel(runId: number): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.base}/${runId}/cancel`, {});
  }
}
```

---

## 5a.7 `LogStreamService`

Wraps the SSE endpoint as an RxJS `Observable<string>`.

```typescript
// core/services/log-stream.service.ts

@Injectable({ providedIn: 'root' })
export class LogStreamService {

  streamLog(runId: number): Observable<string> {
    return new Observable<string>(observer => {
      const url = `/api/runs/${runId}/log-stream`;
      const source = new EventSource(url);

      source.onmessage = (event: MessageEvent<string>) => {
        observer.next(event.data);
      };

      source.addEventListener('done', () => {
        observer.complete();
        source.close();
      });

      source.onerror = (err) => {
        // EventSource auto-reconnects on error — close and error the observable instead
        source.close();
        observer.error(err);
      };

      // Return teardown logic: close the EventSource when the observable is unsubscribed
      return () => {
        source.close();
      };
    });
  }
}
```

---

## 5a.8 HTTP Interceptors

### `auth.interceptor.ts` — attach Bearer token to every API request

```typescript
// core/interceptors/auth.interceptor.ts

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }
  return next(req);
};
```

### `error.interceptor.ts` — redirect to login on 401, show snackbar on 5xx

```typescript
// core/interceptors/error.interceptor.ts

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth    = inject(AuthService);
  const snack   = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        auth.logout();
      } else if (err.status >= 500) {
        snack.open('Server error — please try again', 'Dismiss', { duration: 4000 });
      }
      return throwError(() => err);
    })
  );
};
```

### Register in `app.config.ts`

```typescript
// src/app/app.config.ts

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor, errorInterceptor])
    ),
    provideAnimations(),
  ]
};
```

---

## Phase 5a Acceptance Criteria

- [ ] `ng serve` starts without errors
- [ ] Proxy config forwards `/api/*` to Spring Boot running on port 8080
- [ ] All TypeScript models compile without errors
- [ ] `AuthService.login()` calls `POST /api/auth/login` and stores the token in memory
- [ ] `authInterceptor` attaches `Authorization: Bearer <token>` to every `/api` call
- [ ] `errorInterceptor` redirects to `/login` on a 401 response
- [ ] `LogStreamService.streamLog()` subscribes and receives lines from the SSE endpoint
- [ ] SSE observable completes cleanly when the `done` event is received
- [ ] Unsubscribing from `streamLog()` closes the `EventSource` (verify in browser DevTools)

---

**Previous:** [Phase 4b — Scheduler Integration](./PHASE-4b-Scheduler-Integration.md)  
**Next:** [Phase 5b — Dashboard & Job List](./PHASE-5b-UI-Dashboard-JobList.md)
