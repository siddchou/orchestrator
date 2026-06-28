import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription } from 'rxjs';
import { RunService } from '../../../core/services/run.service';
import { LogStreamService } from '../../../core/services/log-stream.service';
import { StatusBadge } from '../../../shared/components/status-badge/status-badge';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { JobRunDetail } from '../../../core/models/run.model';
import { RunStatus } from '../../../core/models/job.model';

@Component({
  selector: 'app-run-detail',
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatSlideToggleModule, MatTooltipModule,
    StatusBadge, DurationPipe,
  ],
  templateUrl: './run-detail.component.html',
  styleUrl: './run-detail.component.scss',
})
export class RunDetailComponent implements OnInit, OnDestroy {
  private runService = inject(RunService);
  private logStreamService = inject(LogStreamService);
  private route = inject(ActivatedRoute);

  runId: number | null = null;
  run: JobRunDetail | null = null;
  loading = true;

  // Log viewer
  logLines: string[] = [];
  selectedStepId: number | null = null;
  autoScroll = true;
  logComplete = false;
  private logSub?: Subscription;

  ngOnInit() {
    const idParam = this.route.snapshot.paramMap.get('runId');
    if (idParam) {
      this.runId = Number(idParam);
      this.loadRun();
    }
  }

  ngOnDestroy() {
    this.logSub?.unsubscribe();
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
      },
      error: () => { this.loading = false; },
    });
  }

  openLog(stepId: number) {
    if (this.runId == null) return;

    // Unsubscribe from previous log stream
    this.logSub?.unsubscribe();
    this.logLines = [];
    this.selectedStepId = stepId;
    this.logComplete = false;

    if (this.run?.status === 'RUNNING') {
      this.logSub = this.logStreamService.streamLog(this.runId).subscribe({
        next: (line) => {
          this.logLines.push(line);
          if (this.autoScroll) this.scrollToBottom();
        },
        complete: () => {
          this.logComplete = true;
          this.logLines.push('--- Run complete ---');
        },
        error: () => {
          this.logComplete = true;
          this.logLines.push('--- Stream disconnected ---');
        },
      });
    } else {
      // Load static log for completed steps
      this.runService.getStepLog(this.runId, stepId).subscribe({
        next: (res) => {
          if (res.status === 'SUCCESS' && res.data) {
            this.logLines = res.data.split('\n').filter(Boolean);
          }
          this.logComplete = true;
        },
      });
    }
  }

  scrollToBottom() {
    setTimeout(() => {
      const el = document.querySelector('.log-container') as HTMLElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }

  cancelRun() {
    if (this.runId == null) return;
    this.runService.cancelRun(this.runId).subscribe({
      next: () => this.loadRun(),
    });
  }

  goBack() {
    window.location.hash = '#/runs';
  }

  statusIcon(status: RunStatus): string {
    const icons: Record<RunStatus, string> = {
      PENDING: 'schedule', RUNNING: 'pending', SUCCESS: 'check_circle',
      FAILED: 'error', PARTIAL: 'warning', CANCELLED: 'cancel', SKIPPED: 'step_inplace',
    };
    return icons[status] ?? 'help';
  }
}
