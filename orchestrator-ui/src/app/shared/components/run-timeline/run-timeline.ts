import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { JobRunDetail, RunStepDetail } from '@app/core/models/run.model';
import { RunStatus } from '@app/core/models/job.model';

interface StepBar {
  step: RunStepDetail;
  leftPercent: number;
  widthPercent: number;
  colorVar: string;
}

const STATUS_COLOR_VARS: Record<RunStatus, string> = {
  PENDING: 'var(--status-pending-bg, #78909c)',
  RUNNING: 'var(--status-running-bg, #ff9800)',
  SUCCESS: 'var(--status-success-bg, #4caf50)',
  FAILED: 'var(--status-failed-bg, #f44336)',
  PARTIAL: 'var(--status-partial-bg, #ff9800)',
  CANCELLED: 'var(--status-cancelled-bg, #9c27b0)',
};

@Component({
  selector: 'app-run-timeline',
  imports: [CommonModule, MatIconModule],
  templateUrl: './run-timeline.html',
  styleUrl: './run-timeline.scss',
})
export class RunTimelineComponent implements OnChanges {
  @Input() run!: JobRunDetail;

  bars: StepBar[] = [];
  ticks: { label: string; leftPercent: number }[] = [];
  totalDurationMs = 0;

  ngOnChanges(): void {
    if (!this.run?.steps?.length) return;
    this.compute();
  }

  private compute(): void {
    const steps = this.run.steps;
    const times: number[] = [];

    for (const s of steps) {
      if (s.startedAt) times.push(new Date(s.startedAt).getTime());
      if (s.endedAt) times.push(new Date(s.endedAt).getTime());
    }

    if (times.length < 2) {
      this.bars = [];
      this.ticks = [];
      return;
    }

    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);
    this.totalDurationMs = maxTime - minTime || 1000;

    // Compute bar positions
    const minBarWidthPct = 1.5;
    this.bars = steps.map(step => {
      const start = step.startedAt ? new Date(step.startedAt).getTime() : minTime;
      const end = step.endedAt ? new Date(step.endedAt).getTime() : maxTime;
      const leftPct = ((start - minTime) / this.totalDurationMs) * 100;
      let widthPct = ((end - start) / this.totalDurationMs) * 100;
      if (widthPct < minBarWidthPct) widthPct = minBarWidthPct;
      return {
        step,
        leftPercent: leftPct,
        widthPercent: Math.min(widthPct, 100 - leftPct),
        colorVar: STATUS_COLOR_VARS[step.status] ?? '#757575',
      };
    });

    // Generate time axis ticks (up to 6)
    this.ticks = [];
    const tickCount = Math.min(6, Math.max(2, Math.floor(this.totalDurationMs / 5000)));
    for (let i = 0; i <= tickCount; i++) {
      const t = minTime + (this.totalDurationMs * i) / tickCount;
      this.ticks.push({
        label: this.formatDuration(t - minTime),
        leftPercent: (i / tickCount) * 100,
      });
    }
  }

  private formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    const sec = Math.floor(ms / 1000);
    if (sec < 60) return `${sec}s`;
    const min = Math.floor(sec / 60);
    const remSec = sec % 60;
    return `${min}m${remSec}s`;
  }
}
