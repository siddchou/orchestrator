import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin, take } from 'rxjs';
import { RunService } from '@app/core/services/run.service';
import { JobService } from '@app/core/services/job.service';
import { LogStreamService } from '@app/core/services/log-stream.service';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { DurationPipe } from '@app/shared/pipes/duration.pipe';
import { LogViewerComponent } from '../log-viewer/log-viewer.component';
import { RunTimelineComponent } from '@app/shared/components/run-timeline/run-timeline';
import { RunDagCanvasComponent } from '@features/jobs/dag-canvas/run-dag-canvas.component';
import { JobRunDetail } from '@app/core/models/run.model';
import { RunStatus, StepDependency } from '@app/core/models/job.model';

@Component({
  selector: 'app-run-detail',
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatProgressSpinnerModule, MatSlideToggleModule, MatSnackBarModule, RouterLink,
    StatusBadge, DurationPipe, LogViewerComponent, RunTimelineComponent, RunDagCanvasComponent,
  ],
  templateUrl: './run-detail.component.html',
  styleUrl: './run-detail.component.scss',
})
export class RunDetailComponent implements OnInit, OnDestroy {
  private runService = inject(RunService);
  private jobService = inject(JobService);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  runId: number | null = null;
  run: JobRunDetail | null = null;
  stepDependencies: Record<number, StepDependency[]> = {};
  loading = true;

  // Static log viewer for completed steps
  selectedStepId: number | null = null;
  stepLog: string | null = null;
  loadingLog = false;

  // Polling
  private pollInterval?: ReturnType<typeof setInterval>;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('runId');
    if (idParam) {
      this.runId = Number(idParam);
      this.loadRun();
    }
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  loadRun() {
    if (this.runId == null) return;
    this.loading = true;
    this.runService.getRunDetail(this.runId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.run = res.data;
        }
        this.loading = false;

        // Load step dependencies for DAG canvas
        if (this.run?.jobId && this.run.steps.length > 1) {
          this.loadStepDependencies(this.run.jobId);
        }

        // Poll every 3s while the run is active
        if (this.run?.status === 'RUNNING' || this.run?.status === 'PENDING') {
          this.startPolling();
        } else {
          this.stopPolling();
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.stopPolling();
        this.cdr.markForCheck();
      },
    });
  }

  private loadStepDependencies(jobId: number): void {
    this.jobService.getJob(jobId).pipe(take(1)).subscribe({
      next: (jobRes) => {
        if (jobRes.status !== 'SUCCESS' || !this.run) return;
        const jobSteps = jobRes.data.steps;
        if (!jobSteps.length) return;

        // Build mapping from job stepId -> runStepId using stepOrder as bridge
        const stepIdToRunStepId = new Map<number, number>();
        for (const js of jobSteps) {
          const rs = this.run!.steps.find(s => s.stepOrder === js.stepOrder);
          if (rs) stepIdToRunStepId.set(js.stepId, rs.runStepId);
        }

        // Fetch dependencies for each step in parallel
        const depObservables = jobSteps.map(step =>
          this.jobService.getStepDependencies(jobId, step.stepId).pipe(take(1))
        );

        forkJoin(depObservables).subscribe({
          next: (results) => {
            const depsMap: Record<number, StepDependency[]> = {};
            for (let i = 0; i < jobSteps.length; i++) {
              const runStepId = stepIdToRunStepId.get(jobSteps[i].stepId);
              if (runStepId == null) continue;

              let deps: StepDependency[] = [];
              if (results[i].status === 'SUCCESS' && results[i].data) {
                // Transform dependsOnStepId from job stepId to runStepId
                deps = results[i].data.map(d => ({
                  ...d,
                  dependsOnStepId: stepIdToRunStepId.get(d.dependsOnStepId) ?? d.dependsOnStepId,
                }));
              }
              depsMap[runStepId] = deps;
            }
            this.stepDependencies = depsMap;
            this.cdr.markForCheck();
          },
        });
      },
    });
  }

  viewStepLog(stepId: number) {
    if (this.runId == null) return;
    this.selectedStepId = stepId;
    this.loadingLog = true;
    this.stepLog = null;
    this.runService.getStepLog(this.runId, stepId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS' && res.data) {
          this.stepLog = res.data;
        }
        this.loadingLog = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingLog = false;
        this.cdr.markForCheck();
      },
    });
  }

  cancelRun() {
    if (this.runId == null) return;
    this.runService.cancelRun(this.runId).subscribe({
      next: () => {
        this.snack.open('Cancel requested', 'OK', { duration: 2000 });
        this.loadRun();
      },
      error: () => {
        this.snack.open('Cancel failed', 'Dismiss', { duration: 3000 });
      },
    });
  }

  isActive(): boolean {
    return this.run?.status === 'RUNNING' || this.run?.status === 'PENDING';
  }

  statusIcon(status: RunStatus): string {
    const icons: Record<RunStatus, string> = {
      PENDING: 'schedule', RUNNING: 'pending', SUCCESS: 'check_circle',
      FAILED: 'error', PARTIAL: 'warning', CANCELLED: 'cancel',
    };
    return icons[status] ?? 'help';
  }

  statusIconColor(status: RunStatus): string {
    const colors: Record<RunStatus, string> = {
      SUCCESS: 'green', FAILED: 'red', RUNNING: 'orange',
      PENDING: 'grey', PARTIAL: 'orange', CANCELLED: 'purple',
    };
    return colors[status] ?? 'grey';
  }

  private startPolling(): void {
    this.stopPolling();
    this.pollInterval = setInterval(() => this.loadRun(), 3000);
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = undefined;
    }
  }
}
