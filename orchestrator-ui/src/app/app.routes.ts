import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadChildren: () => import('./features/dashboard/dashboard.component').then(m => [{ component: m.DashboardComponent }]),
  },
  {
    path: 'jobs',
    loadChildren: () => import('./features/jobs/job-list/job-list.component').then(m => [{ component: m.JobListComponent }]),
  },
  {
    path: 'jobs/new',
    loadChildren: () => import('./features/jobs/job-detail/job-detail.component').then(m => [{ component: m.JobDetailComponent }]),
  },
  {
    path: 'jobs/:id',
    loadChildren: () => import('./features/jobs/job-detail/job-detail.component').then(m => [{ component: m.JobDetailComponent }]),
  },
  {
    path: 'runs',
    loadChildren: () => import('./features/runs/run-list/run-list.component').then(m => [{ component: m.RunListComponent }]),
  },
  {
    path: 'runs/:runId',
    loadChildren: () => import('./features/runs/run-detail/run-detail.component').then(m => [{ component: m.RunDetailComponent }]),
  },
  {
    path: 'config',
    loadChildren: () => import('./features/config/global-config/global-config.component').then(m => [{ component: m.GlobalConfigComponent }]),
  },
  { path: '**', redirectTo: '/dashboard' },
];
