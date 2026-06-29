# Phase 5e — UI: Routing, Global Config & Build Integration

> **Goal:** Wire all routes, build the app shell (sidenav layout), the Global Config
> page, the Login page, and configure the Maven build so Angular is packaged inside
> the Spring Boot JAR automatically.

> **Depends on:** Phase 5a–5d (all feature components)  
> **Produces:** `app.routes.ts`, `AppComponent`, `GlobalConfigComponent`,
> `LoginComponent`, Maven frontend plugin config, Spring Boot SPA fallback

---

## 5e.1 App Routes

```typescript
// src/app/app.routes.ts

import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },

  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },

  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },

  {
    path: 'jobs',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/jobs/job-list/job-list.component').then(m => m.JobListComponent)
  },
  {
    path: 'jobs/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent)
  },
  {
    path: 'jobs/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent)
  },

  {
    path: 'runs',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/runs/run-list/run-list.component').then(m => m.RunListComponent)
  },
  {
    path: 'runs/:runId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/runs/run-detail/run-detail.component').then(m => m.RunDetailComponent)
  },

  {
    path: 'config',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/config/global-config/global-config.component').then(m => m.GlobalConfigComponent)
  },

  { path: '**', redirectTo: '/dashboard' }
];
```

---

## 5e.2 Auth Guard

```typescript
// core/guards/auth.guard.ts

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;
  router.navigate(['/login']);
  return false;
};
```

---

## 5e.3 App Shell — Sidenav Layout

```typescript
// src/app/app.component.ts

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatIconModule, MatButtonModule, CommonModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  constructor(public auth: AuthService) {}

  logout(): void { this.auth.logout(); }
}
```

```html
<!-- src/app/app.component.html -->

<mat-sidenav-container class="sidenav-container" *ngIf="auth.isLoggedIn(); else loginPage">

  <mat-sidenav mode="side" opened class="sidenav">
    <div class="brand">
      <mat-icon>settings_input_composite</mat-icon>
      <span>Orchestrator</span>
    </div>

    <mat-nav-list>
      <a mat-list-item routerLink="/dashboard" routerLinkActive="active">
        <mat-icon matListItemIcon>dashboard</mat-icon>
        <span matListItemTitle>Dashboard</span>
      </a>
      <a mat-list-item routerLink="/jobs" routerLinkActive="active">
        <mat-icon matListItemIcon>work</mat-icon>
        <span matListItemTitle>Jobs</span>
      </a>
      <a mat-list-item routerLink="/runs" routerLinkActive="active">
        <mat-icon matListItemIcon>history</mat-icon>
        <span matListItemTitle>Run History</span>
      </a>
      <a mat-list-item routerLink="/config" routerLinkActive="active">
        <mat-icon matListItemIcon>tune</mat-icon>
        <span matListItemTitle>Global Config</span>
      </a>
    </mat-nav-list>

    <div class="sidenav-footer">
      <button mat-button (click)="logout()">
        <mat-icon>logout</mat-icon> Logout
      </button>
    </div>
  </mat-sidenav>

  <mat-sidenav-content>
    <router-outlet />
  </mat-sidenav-content>

</mat-sidenav-container>

<!-- When not logged in, just render the login route -->
<ng-template #loginPage>
  <router-outlet />
</ng-template>
```

```scss
/* src/app/app.component.scss */
.sidenav-container { height: 100vh; }
.sidenav { width: 220px; background: #1a237e; color: white; display: flex; flex-direction: column; }
.brand { display: flex; align-items: center; gap: 10px; padding: 20px 16px; font-size: 18px; font-weight: 700; }
.sidenav .mat-mdc-list-item { color: rgba(255,255,255,0.85); }
.sidenav .active { background: rgba(255,255,255,0.15); border-radius: 4px; }
.sidenav-footer { margin-top: auto; padding: 12px; }
.sidenav-footer button { color: rgba(255,255,255,0.7); width: 100%; }
mat-sidenav-content { padding: 0; overflow: auto; }
```

---

## 5e.4 Login Component

```typescript
// features/auth/login/login.component.ts

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatCardModule, MatSnackBarModule, CommonModule
  ],
  template: `
    <div class="login-wrapper">
      <mat-card class="login-card">
        <mat-card-header>
          <mat-card-title>Job Orchestrator</mat-card-title>
          <mat-card-subtitle>Sign in to continue</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="login()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" autocomplete="username">
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password"
                     autocomplete="current-password">
            </mat-form-field>
            <button mat-raised-button color="primary" type="submit"
                    class="full-width" [disabled]="form.invalid || loading">
              {{ loading ? 'Signing in…' : 'Sign In' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-wrapper { display: flex; justify-content: center; align-items: center; height: 100vh; background: #f5f5f5; }
    .login-card { width: 360px; padding: 16px; }
    .full-width { width: 100%; margin-bottom: 12px; }
  `]
})
export class LoginComponent {
  form = new FormGroup({
    username: new FormControl('', Validators.required),
    password: new FormControl('', Validators.required)
  });
  loading = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private snack: MatSnackBar
  ) {}

  login(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.auth.login(this.form.value.username!, this.form.value.password!).subscribe({
      next: () => { this.router.navigate(['/dashboard']); },
      error: () => {
        this.loading = false;
        this.snack.open('Invalid username or password', 'Dismiss', { duration: 4000 });
      }
    });
  }
}
```

---

## 5e.5 Global Config Component

```typescript
// features/config/global-config/global-config.component.ts

@Component({
  selector: 'app-global-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatTableModule, MatSnackBarModule
  ],
  templateUrl: './global-config.component.html'
})
export class GlobalConfigComponent implements OnInit {

  globalVars: EnvVar[] = [];
  newVarName  = '';
  newVarValue = '';

  // Path validator
  validateForm = new FormGroup({
    javaHome:   new FormControl(''),
    workingDir: new FormControl('')
  });
  validation: Record<string, string> = {};

  constructor(
    private http: HttpClient,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void { this.loadGlobalVars(); }

  loadGlobalVars(): void {
    this.http.get<ApiResponse<EnvVar[]>>('/api/env-vars/global')
      .subscribe(resp => { this.globalVars = resp.data; });
  }

  addVar(): void {
    if (!this.newVarName.trim() || !this.newVarValue.trim()) return;
    this.http.post<ApiResponse<EnvVar>>('/api/env-vars/global', {
      varName: this.newVarName.trim(), varValue: this.newVarValue.trim()
    }).subscribe(resp => {
      this.globalVars = [...this.globalVars, resp.data];
      this.newVarName = '';
      this.newVarValue = '';
      this.snack.open('Variable added', 'OK', { duration: 2000 });
    });
  }

  deleteVar(v: EnvVar): void {
    this.http.delete(`/api/env-vars/global/${v.envId}`)
      .subscribe(() => {
        this.globalVars = this.globalVars.filter(e => e.envId !== v.envId);
        this.snack.open('Variable removed', 'OK', { duration: 2000 });
      });
  }

  validatePaths(): void {
    const p = this.validateForm.value;
    this.http.get<ApiResponse<Record<string, string>>>('/api/system/env-validate', {
      params: { javaHome: p.javaHome ?? '', workingDir: p.workingDir ?? '' }
    }).subscribe(resp => { this.validation = resp.data; });
  }
}
```

```html
<!-- global-config.component.html -->
<div class="config-container">
  <h2>Global Configuration</h2>

  <!-- Global Env Vars -->
  <mat-card>
    <mat-card-header><mat-card-title>Global Environment Variables</mat-card-title>
      <mat-card-subtitle>Inherited by all jobs (job-level vars override these)</mat-card-subtitle>
    </mat-card-header>
    <mat-card-content>
      <div class="add-row">
        <input [(ngModel)]="newVarName"  placeholder="VAR_NAME"  class="var-input">
        <input [(ngModel)]="newVarValue" placeholder="value"     class="var-input wide">
        <button mat-raised-button (click)="addVar()">Add</button>
      </div>
      <table class="env-table">
        <tr *ngFor="let v of globalVars">
          <td class="mono">{{ v.varName }}</td>
          <td class="mono">{{ v.varValue }}</td>
          <td><button mat-icon-button color="warn" (click)="deleteVar(v)">
            <mat-icon>delete</mat-icon></button></td>
        </tr>
        <tr *ngIf="globalVars.length === 0">
          <td colspan="3" class="empty">No global variables defined.</td>
        </tr>
      </table>
    </mat-card-content>
  </mat-card>

  <!-- Path Validator -->
  <mat-card style="margin-top:24px">
    <mat-card-header><mat-card-title>Path Validator</mat-card-title>
      <mat-card-subtitle>Check JAVA_HOME and working directories exist on the server</mat-card-subtitle>
    </mat-card-header>
    <mat-card-content [formGroup]="validateForm">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>JAVA_HOME</mat-label>
        <input matInput formControlName="javaHome" placeholder="/usr/lib/jvm/java-21">
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Working Directory</mat-label>
        <input matInput formControlName="workingDir" placeholder="/opt/jobs/my-job">
      </mat-form-field>
      <button mat-raised-button (click)="validatePaths()">Validate</button>

      <div class="validation-results" *ngIf="validation['javaHome']">
        <div *ngFor="let kv of validation | keyvalue"
             [class.ok]="kv.value === 'OK'" [class.error]="kv.value !== 'OK'">
          {{ kv.key }}: <strong>{{ kv.value }}</strong>
        </div>
      </div>
    </mat-card-content>
  </mat-card>
</div>
```

---

## 5e.6 Maven Build Integration

### `orchestrator-api/pom.xml` — frontend-maven-plugin

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.github.eirslett</groupId>
      <artifactId>frontend-maven-plugin</artifactId>
      <version>1.14.2</version>
      <configuration>
        <workingDirectory>../orchestrator-ui</workingDirectory>
        <nodeVersion>v20.11.0</nodeVersion>
        <npmVersion>10.2.4</npmVersion>
      </configuration>
      <executions>
        <execution>
          <id>install node and npm</id>
          <goals><goal>install-node-and-npm</goal></goals>
          <phase>generate-resources</phase>
        </execution>
        <execution>
          <id>npm install</id>
          <goals><goal>npm</goal></goals>
          <phase>generate-resources</phase>
          <configuration><arguments>install</arguments></configuration>
        </execution>
        <execution>
          <id>npm build</id>
          <goals><goal>npm</goal></goals>
          <phase>generate-resources</phase>
          <configuration><arguments>run build</arguments></configuration>
        </execution>
      </executions>
    </plugin>

    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

### Spring Boot SPA Fallback Controller

Angular's router handles `/jobs/42`, `/runs/7`, etc. — but if the user hits refresh,
Spring Boot receives those paths and returns 404. Fix with a fallback controller:

```java
// com.yourco.orchestrator.config.SpaFallbackController

@Controller
public class SpaFallbackController {

    /**
     * Forward any path that:
     * - is not an API call (/api/**)
     * - is not a static asset (has no file extension)
     * to index.html so Angular's router can take over.
     */
    @GetMapping({
        "/",
        "/{path:[^\\.]*}",
        "/{path:(?!api)[^\\.]*}/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
```

---

## 5e.7 `angular.json` — Production Build Config

```json
"configurations": {
  "production": {
    "budgets": [
      { "type": "initial", "maximumWarning": "2mb", "maximumError": "5mb" }
    ],
    "outputHashing": "all",
    "optimization": true,
    "sourceMap": false,
    "namedChunks": false,
    "aot": true,
    "buildOptimizer": true
  }
}
```

---

## Phase 5e Acceptance Criteria

- [ ] `ng build --configuration production` succeeds and outputs to `orchestrator-api/src/main/resources/static/`
- [ ] `mvn clean package` compiles Angular first, then packages it into the fat JAR
- [ ] Navigating to `http://localhost:8080` loads the app
- [ ] Hard refresh on `/jobs/42` serves `index.html` (not 404) — Angular router takes over
- [ ] Unauthenticated user visiting `/dashboard` is redirected to `/login`
- [ ] Login with valid credentials stores token and navigates to dashboard
- [ ] Login with invalid credentials shows a snackbar error
- [ ] Logout clears token and redirects to `/login`
- [ ] Sidenav active link highlights the current route
- [ ] Global Config page loads global env vars, adds a new one, and deletes one correctly
- [ ] Path Validator shows per-field `OK` / `NOT_FOUND` / `NOT_EXECUTABLE` results

---

**Previous:** [Phase 5d — Run Monitor & Log Viewer](./PHASE-5d-UI-RunMonitor-LogViewer.md)  
**Next:** [Phase 6a — Security: JWT Auth](./PHASE-6a-Security-JWT-Auth.md)
