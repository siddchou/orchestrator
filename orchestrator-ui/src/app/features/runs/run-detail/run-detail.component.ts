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
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { RunService } from '@app/core/services/run.service';
import { LogStreamService } from '@app/core/services/log-stream.service';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { DurationPipe } from '@app/shared/pipes/duration.pipe';
import { LogViewerComponent } from '../log-viewer/log-viewer.component';
import { JobRunDetail } from '@app/core/models/run.model';
import { RunStatus } from '@app/core/models/job.model';

@Component({
  selector: 'app-run-detail',
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatProgressSpinnerModule, MatSlideToggleModule, MatTooltipModule, MatSnackBarModule, RouterLink,
    StatusBadge, DurationPipe, LogViewerComponent,
  ],
  templateUrl: './run-detail.component.html',
  styleUrl: './run-detail.component.scss',
})
export class RunDetailComponent implements OnInit, OnDestroy {
  private runService = inject(RunService);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  runId: number | null = null;
  run: JobRunDetail | null = null;
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
