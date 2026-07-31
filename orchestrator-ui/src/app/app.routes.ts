import { Routes } from '@angular/router';
import { authGuard, adminGuard } from '@app/core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('@features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('@features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'jobs',
        loadComponent: () => import('@features/jobs/job-list/job-list.component').then(m => m.JobListComponent),
      },
      {
        path: 'jobs/new',
        loadComponent: () => import('@features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent),
      },
      {
        path: 'jobs/:id/canvas',
        loadComponent: () => import('@features/jobs/dag-canvas-stub/dag-canvas-stub.component')
          .then(m => m.DagCanvasStubComponent),
      },
      {
        path: 'jobs/:id',
        loadComponent: () => import('@features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent),
      },
      {
        path: 'runs',
        loadComponent: () => import('@features/runs/run-list/run-list.component').then(m => m.RunListComponent),
      },
      {
        path: 'runs/:runId',
        loadComponent: () => import('@features/runs/run-detail/run-detail.component').then(m => m.RunDetailComponent),
      },
      {
        path: 'config',
        canActivate: [adminGuard],
        loadComponent: () => import('@features/config/global-config/global-config.component').then(m => m.GlobalConfigComponent),
      },
      {
        path: 'credentials',
        canActivate: [adminGuard],
        loadComponent: () => import('@features/credentials/credential-list.component')
          .then(m => m.CredentialListComponent),
      },
      { path: '**', redirectTo: '/dashboard' },
    ],
  },
];
