import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { RunService } from '@app/core/services/run.service';
import { JobService } from '@app/core/services/job.service';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { DurationPipe } from '@app/shared/pipes/duration.pipe';
import { JobRunSummary } from '@app/core/models/run.model';
import { RunStatus } from '@app/core/models/job.model';
import { RunJobDialog } from '@app/shared/components/run-job-dialog/run-job-dialog';

const CARD_DATA = [
  { label: 'Total Jobs', icon: 'work', key: 'totalJobs' as const },
  { label: 'Runs Today', icon: 'play_arrow', key: 'runsToday' as const },
  { label: 'Success Rate', icon: 'trending_up', key: 'successRate' as const },
  { label: 'Running Now', icon: 'pending', key: 'runningNow' as const },
];

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule, MatCardModule, MatTableModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatSnackBarModule, MatDialogModule,
    MatProgressSpinnerModule,
    RouterLink, StatusBadge, DurationPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, OnDestroy {
  private runService = inject(RunService);
  private jobService = inject(JobService);
  private dialog = inject(MatDialog);
  private cd = inject(ChangeDetectorRef);

  cardData = CARD_DATA;
  summary = { totalJobs: 0, runsToday: 0, successRate: 0, runningNow: 0 };
  recentRuns: JobRunSummary[] = [];
  displayedColumns = ['jobName', 'status', 'triggerType', 'startedAt', 'duration', 'actions'];

  isLoading = false;

  private pollSub?: Subscription;

  ngOnInit() {
    this.loadDashboard();
    this.pollSub = interval(30000).subscribe(() => this.loadDashboard());
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  loadDashboard() {
    this.isLoading = true;
    this.cd.markForCheck();

    this.jobService.listJobs(0, 1).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.summary.totalJobs = res.data.totalElements;
        }
        this.cd.detectChanges();
      },
    });

    this.runService.listRuns(0, 50).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          const runs = res.data.content;
          const today = new Date().toDateString();
          this.summary.runsToday = runs.filter(r => new Date(r.startedAt).toDateString() === today).length;

          const completed = runs.filter(r => ['SUCCESS', 'FAILED', 'PARTIAL'].includes(r.status));
          this.summary.successRate = completed.length
            ? Math.round((completed.filter(r => r.status === 'SUCCESS').length / completed.length) * 100)
            : 0;

          this.summary.runningNow = runs.filter(r => r.status === 'RUNNING').length;
          this.recentRuns = runs.slice(0, 10);
        }
        this.isLoading = false;
        this.cd.detectChanges();
      },
    });
  }

  triggerRun(jobName: string, jobId: number) {
    this.jobService.getJob(jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.dialog.open(RunJobDialog, {
            data: { job: res.data },
          }).afterClosed().subscribe(confirmed => {
            if (!confirmed) return;
            this.jobService.triggerRun(jobId).subscribe({
              next: (runRes) => {
                if (runRes.status === 'SUCCESS') {
                  this.loadDashboard();
                }
              },
            });
          });
        }
      },
    });
  }

  statusColor(status: RunStatus): string {
    const colors: Record<RunStatus, string> = {
      PENDING: '#757575', RUNNING: '#ff9800', SUCCESS: '#4caf50',
      FAILED: '#f44336', PARTIAL: '#9c27b0', CANCELLED: '#607d8b',
    };
    return colors[status] ?? '#757575';
  }

  viewRun(runId: number) {
    window.location.hash = `#/runs/${runId}`;
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
}
