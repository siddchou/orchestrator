# Phase 5 — Angular UI

> **Goal:** Build a configuration-driven Angular frontend that consumes the Phase 3 API.
> Covers four modules: Dashboard, Job Manager, Run Monitor, and Global Config.
> Live log streaming uses the browser's native `EventSource` API against the SSE endpoint.

---

## 5.1 Tech Stack

| Concern | Choice |
|---------|--------|
| Framework | Angular 17+ (standalone components) |
| UI Components | Angular Material (MDC-based) |
| HTTP | Angular `HttpClient` with typed responses |
| State | Component-level signals + RxJS (no NgRx unless needed) |
| Routing | `provideRouter` with lazy-loaded routes |
| Forms | Reactive Forms (`FormGroup`, `FormControl`) |
| SSE | Browser native `EventSource` |
| Build | Angular CLI, output to `dist/orchestrator-ui/` |

---

## 5.2 Project Setup

```bash
ng new orchestrator-ui --standalone --routing --style=scss
cd orchestrator-ui
ng add @angular/material
```

### `angular.json` — output path for Spring Boot embedding

```json
"outputPath": "../orchestrator-api/src/main/resources/static"
```

Maven build step in `orchestrator-api/pom.xml`:

```xml
<plugin>
  <groupId>com.github.eirslett</groupId>
  <artifactId>frontend-maven-plugin</artifactId>
  <version>1.14.2</version>
  <configuration>
    <workingDirectory>../orchestrator-ui</workingDirectory>
    <nodeVersion>v20.11.0</nodeVersion>
  </configuration>
  <executions>
    <execution>
      <id>npm install</id>
      <goals><goal>npm</goal></goals>
      <configuration><arguments>install</arguments></configuration>
    </execution>
    <execution>
      <id>npm build</id>
      <goals><goal>npm</goal></goals>
      <configuration><arguments>run build</arguments></configuration>
    </execution>
  </executions>
</plugin>
```

---

## 5.3 App Structure

```
src/app/
├── core/
│   ├── services/
│   │   ├── job.service.ts         ← CRUD for jobs, steps, env vars, schedules
│   │   ├── run.service.ts         ← run history, detail, cancel
│   │   ├── log-stream.service.ts  ← SSE wrapper
│   │   └── auth.service.ts        ← JWT login, token storage
│   ├── interceptors/
│   │   ├── auth.interceptor.ts    ← attach Bearer token
│   │   └── error.interceptor.ts   ← global error toast
│   └── models/
│       ├── job.model.ts
│       ├── run.model.ts
│       └── api-response.model.ts
│
├── features/
│   ├── dashboard/
│   │   └── dashboard.component.ts
│   ├── jobs/
│   │   ├── job-list/
│   │   ├── job-detail/
│   │   ├── step-builder/
│   │   │   └── step-form/         ← dynamic form per StepType
│   │   └── schedule-editor/
│   ├── runs/
│   │   ├── run-list/
│   │   ├── run-detail/
│   │   └── log-viewer/
│   └── config/
│       └── global-config/
│
├── shared/
│   ├── components/
│   │   ├── status-badge/
│   │   ├── confirm-dialog/
│   │   └── cron-builder/
│   └── pipes/
│       └── duration.pipe.ts
│
└── app.routes.ts
```

---

## 5.4 Core Models

```typescript
// api-response.model.ts
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

// job.model.ts
export type StepType = 'ENV_SETUP' | 'LOG_CLEANUP' | 'JAVA_EXEC' | 'SFTP' | 'ARCHIVE';
export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED';
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'API';

export interface JobDefinition {
  jobId: number;
  jobName: string;
  description: string;
  workingDir: string;
  enabled: boolean;
  steps: JobStep[];
  envVars: EnvVar[];
  schedule: JobSchedule | null;
}

export interface JobStep {
  stepId: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;   // JSON string — parsed by step-form component
  continueOnFailure: boolean;
  enabled: boolean;
}

export interface JobSchedule {
  scheduleId: number;
  cronExpression: string;
  enabled: boolean;
  nextFireTime: string | null;
}

// run.model.ts
export interface JobRunSummary {
  runId: number;
  jobId: number;
  jobName: string;
  status: RunStatus;
  triggerType: TriggerType;
  triggeredBy: string;
  startedAt: string;
  endedAt: string;
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
  exitCode: number;
  startedAt: string;
  endedAt: string;
  durationSeconds: number;
}
```

---

## 5.5 Core Services

### `job.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class JobService {
  private api = '/api';

  constructor(private http: HttpClient) {}

  listJobs(page = 0, size = 20, search = ''): Observable<ApiResponse<Page<JobDefinition>>> {
    return this.http.get<ApiResponse<Page<JobDefinition>>>(
      `${this.api}/jobs`, { params: { page, size, search } }
    );
  }

  getJob(id: number): Observable<ApiResponse<JobDefinition>> {
    return this.http.get<ApiResponse<JobDefinition>>(`${this.api}/jobs/${id}`);
  }

  createJob(body: Partial<JobDefinition>): Observable<ApiResponse<JobDefinition>> {
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

  triggerRun(id: number): Observable<ApiResponse<JobRunSummary>> {
    return this.http.post<ApiResponse<JobRunSummary>>(`${this.api}/jobs/${id}/run`, {});
  }

  reorderSteps(jobId: number, stepIds: number[]): Observable<ApiResponse<JobStep[]>> {
    return this.http.put<ApiResponse<JobStep[]>>(
      `${this.api}/jobs/${jobId}/steps/reorder`, { stepIds }
    );
  }
}
```

### `log-stream.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class LogStreamService {

  streamLog(runId: number): Observable<string> {
    return new Observable<string>(observer => {
      const source = new EventSource(`/api/runs/${runId}/log-stream`);

      source.onmessage = (event: MessageEvent) => {
        observer.next(event.data as string);
      };

      source.addEventListener('done', () => {
        observer.complete();
        source.close();
      });

      source.onerror = (err) => {
        observer.error(err);
        source.close();
      };

      // Cleanup when unsubscribed
      return () => source.close();
    });
  }
}
```

---

## 5.6 Module Specifications

### Dashboard

Display four summary cards and a recent runs table.

```
┌──────────────┬──────────────┬──────────────┬──────────────┐
│  Total Jobs  │  Runs Today  │ Success Rate │  Running Now │
│     24       │     17       │   94.1 %     │      2       │
└──────────────┴──────────────┴──────────────┴──────────────┘

Recent Runs
┌─────────────────┬──────────┬──────────┬──────────┬────────┐
│ Job Name        │ Status   │ Trigger  │ Started  │ Run    │
├─────────────────┼──────────┼──────────┼──────────┼────────┤
│ DailyReport     │ SUCCESS  │ SCHEDULED│ 02:00    │ View   │
│ DataSync        │ RUNNING  │ MANUAL   │ 09:15    │ View   │
└─────────────────┴──────────┴──────────┴──────────┴────────┘
```

**Implementation notes:**
- Poll `/api/runs?size=10&sort=createdAt,desc` every 30 seconds
- Status badges color-coded: green = SUCCESS, red = FAILED, amber = RUNNING,
  grey = PARTIAL/CANCELLED
- Quick-launch button opens a confirmation dialog then calls `triggerRun()`

---

### Job Manager

Two views: job list and job detail/edit.

**Job List:**
- Searchable, paginated `mat-table`
- Per-row actions: Edit, Run Now, Delete, Enable/Disable toggle

**Job Detail Form:**

```
Tab 1: General
  ┌─ Job Name ──────────────────────────────────────────────┐
  │  Daily Report Generator                                  │
  ├─ Description ───────────────────────────────────────────┤
  │  Generates and emails the daily sales PDF               │
  ├─ Working Directory ─────────────────────────────────────┤
  │  /opt/jobs/daily-report          [Validate Path]        │
  └─────────────────────────────────────────────────────────┘

Tab 2: Steps  [+ Add Step]
  ╔══╦═══════════════════╦══════════════╦══════════════════╗
  ║ ≡ ║ 1. Setup Java Env ║ ENV_SETUP    ║ [Edit] [Delete]  ║
  ║ ≡ ║ 2. Cleanup Logs   ║ LOG_CLEANUP  ║ [Edit] [Delete]  ║
  ║ ≡ ║ 3. Run Generator  ║ JAVA_EXEC    ║ [Edit] [Delete]  ║
  ║ ≡ ║ 4. Upload Report  ║ SFTP         ║ [Edit] [Delete]  ║
  ║ ≡ ║ 5. Archive Logs   ║ ARCHIVE      ║ [Edit] [Delete]  ║
  ╚══╩═══════════════════╩══════════════╩══════════════════╝
  (Drag ≡ handle to reorder)

Tab 3: Environment Variables
  [+ Add Variable]
  ┌──────────────────────┬──────────────────────────────────┐
  │ REPORT_OUTPUT_DIR    │ /opt/jobs/daily-report/output    │
  │ SMTP_HOST            │ mail.company.com                 │
  └──────────────────────┴──────────────────────────────────┘

Tab 4: Schedule
  Cron Expression:  [0 0 2 * * *] [Validate]
  Next fires:  Tomorrow 02:00 · In 2 days 02:00 · In 3 days 02:00
  [Enable Schedule]  [Disable Schedule]
```

**Step Form Dialog — dynamic fields per `StepType`:**

| StepType | Fields Shown |
|----------|-------------|
| ENV_SETUP | JAVA_HOME path, classpath entries (multi-value chip input), extra env vars table |
| LOG_CLEANUP | Directory path, file pattern (glob input with examples) |
| JAVA_EXEC | Main class OR JAR path toggle, JVM args chip input, program args chip input, timeout |
| SFTP | Host, port, username, credential reference (dropdown of stored creds), remote dir, file pattern, direction toggle |
| ARCHIVE | Source dir, file patterns (chip input), archive dir, format (ZIP/TAR_GZ radio) |

Common fields on all step forms:
- Step name
- Continue on failure toggle
- Enabled toggle

---

### Run Monitor

**Run List:**
- Filter by job, status, date range
- Paginated table with status badges and duration
- Click row → Run Detail

**Run Detail:**

```
Run #1042 · DailyReport · SUCCESS · 14 Nov 2025 02:00 · Duration: 2m 34s
[Cancel]  (shown only if RUNNING)

Step Timeline
  ✓ 1. Setup Java Env     ENV_SETUP    0.3s   SUCCESS   [View Log]
  ✓ 2. Cleanup Logs       LOG_CLEANUP  0.1s   SUCCESS   [View Log]
  ✓ 3. Run Generator      JAVA_EXEC    2m 18s SUCCESS   [View Log]
  ✓ 4. Upload Report      SFTP         4.2s   SUCCESS   [View Log]
  ✓ 5. Archive Logs       ARCHIVE      2.1s   SUCCESS   [View Log]
```

**Live Log Viewer (for RUNNING jobs):**

```
╔═══════════════════════════════════════════════════════════════╗
║ LIVE LOG — Step 3: Run Generator           [Auto-scroll ON]  ║
╠═══════════════════════════════════════════════════════════════╣
║ 2025-11-14 02:00:14 INFO  Starting report generation...      ║
║ 2025-11-14 02:00:14 INFO  Connecting to Oracle...            ║
║ 2025-11-14 02:00:15 INFO  Query executed. 14,822 rows.       ║
║ 2025-11-14 02:00:16 INFO  Generating PDF...                  ║
║ _                                                             ║
╚═══════════════════════════════════════════════════════════════╝
```

**Log Viewer implementation:**

```typescript
@Component({
  selector: 'app-log-viewer',
  template: `
    <div class="log-container" #logContainer>
      <pre>{{ logLines().join('\n') }}</pre>
    </div>
    <mat-slide-toggle [(ngModel)]="autoScroll">Auto-scroll</mat-slide-toggle>
  `
})
export class LogViewerComponent implements OnInit, OnDestroy {
  @Input() runId!: number;
  @ViewChild('logContainer') logContainer!: ElementRef;

  logLines = signal<string[]>([]);
  autoScroll = true;
  private sub?: Subscription;

  constructor(private logStream: LogStreamService) {}

  ngOnInit() {
    this.sub = this.logStream.streamLog(this.runId).subscribe({
      next: line => {
        this.logLines.update(lines => [...lines, line]);
        if (this.autoScroll) {
          this.scrollToBottom();
        }
      },
      complete: () => {
        this.logLines.update(lines => [...lines, '--- Run complete ---']);
      }
    });
  }

  private scrollToBottom() {
    setTimeout(() => {
      const el = this.logContainer.nativeElement;
      el.scrollTop = el.scrollHeight;
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
```

---

### Global Config

Two sections:

**Global Environment Variables**
- Same inline-edit table as job-level env vars
- Changes apply to all jobs immediately

**System Health Panel**
- Shows: DB connection status, thread pool utilization, active run count
- Path validator form: enter `JAVA_HOME` and `workingDir`, click Validate → calls
  `GET /api/system/env-validate`

---

## 5.7 Routing

```typescript
// app.routes.ts
export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', loadComponent: () =>
    import('./features/dashboard/dashboard.component') },
  { path: 'jobs', loadComponent: () =>
    import('./features/jobs/job-list/job-list.component') },
  { path: 'jobs/new', loadComponent: () =>
    import('./features/jobs/job-detail/job-detail.component') },
  { path: 'jobs/:id', loadComponent: () =>
    import('./features/jobs/job-detail/job-detail.component') },
  { path: 'runs', loadComponent: () =>
    import('./features/runs/run-list/run-list.component') },
  { path: 'runs/:runId', loadComponent: () =>
    import('./features/runs/run-detail/run-detail.component') },
  { path: 'config', loadComponent: () =>
    import('./features/config/global-config/global-config.component') },
  { path: 'login', loadComponent: () =>
    import('./features/auth/login/login.component') },
  { path: '**', redirectTo: '/dashboard' }
];
```

---

## 5.8 Auth Interceptor

```typescript
@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private auth: AuthService, private router: Router) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.auth.getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
    return next.handle(req).pipe(
      catchError(err => {
        if (err.status === 401) {
          this.auth.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => err);
      })
    );
  }
}
```

---

## Phase 5 Acceptance Criteria

- [x] App builds cleanly with `ng build --configuration production`
- [x] Built static assets copy into Spring Boot's `resources/static/` correctly
- [x] Angular routes work on hard refresh (Spring Boot serves `index.html` for unknown paths)
- [x] Job list loads, paginates, and searches correctly
- [x] Step builder drag-and-drop reorder calls the reorder API and updates step numbers
- [x] Step type selection dynamically renders the correct form fields
- [x] Run Monitor live log viewer streams output and auto-scrolls
- [x] SSE stream closes cleanly when run completes — no memory leak on `EventSource`
- [x] Dashboard cards and table refresh automatically every 30 seconds
- [x] Schedule cron validator shows next 3 fire times before saving

---

## Implementation Notes

### What was built
- Angular 22 with Zoneless Change Detection, Material 22, SCSS
- All four feature modules: Dashboard, Job Manager, Run Monitor, Global Config
- Hash-location routing with lazy-loaded components
- Build output to `src/main/resources/static` via `angular.json`
- `WebConfig.java` forwards root paths to `browser/index.html` for SPA routing
- `frontend-maven-plugin` in `pom.xml` for Node/npm install + build during Maven lifecycle

### Key fixes during build
- Switched `loadComponent` → `loadChildren` with `.then(m => [{ component: m.X }])` to resolve ESM interop type errors
- Added `@angular/animations` dependency for `provideAnimationsAsync`
- Fixed `MAT_DIALOG_DATA<ConfirmData>` generic incompatibility with `inject()` + type cast
- Replaced `snackBar.error()` → `snackBar.open()` with `panelClass` for Angular 22 API
- Used `HttpParams` builder to avoid undefined param values in HttpClient

---

**Previous:** [Phase 4 — Scheduling](./PHASE-4-Scheduling.md)  
**Next:** [Phase 6 — Security](./PHASE-6-Security.md)
