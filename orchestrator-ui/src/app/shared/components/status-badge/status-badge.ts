import { Component, Input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { RunStatus } from '../../../core/models/job.model';

const STATUS_COLORS: Record<RunStatus, { foreground: string; background: string }> = {
  PENDING:   { foreground: '#ffffff', background: '#757575' },
  RUNNING:   { foreground: '#ffffff', background: '#ff9800' },
  SUCCESS:   { foreground: '#ffffff', background: '#4caf50' },
  FAILED:    { foreground: '#ffffff', background: '#f44336' },
  PARTIAL:   { foreground: '#ffffff', background: '#9c27b0' },
  CANCELLED: { foreground: '#ffffff', background: '#607d8b' },
};

const STATUS_LABELS: Record<RunStatus, string> = {
  PENDING:   'Pending',
  RUNNING:   'Running',
  SUCCESS:   'Success',
  FAILED:    'Failed',
  PARTIAL:   'Partial',
  CANCELLED: 'Cancelled',
};

@Component({
  selector: 'app-status-badge',
  imports: [MatChipsModule],
  templateUrl: './status-badge.html',
  styleUrl: './status-badge.scss',
})
export class StatusBadge {
  @Input() status: RunStatus = 'PENDING';

  get color(): string {
    return STATUS_COLORS[this.status]?.background ?? '#757575';
  }

  get statusLabel(): string {
    return STATUS_LABELS[this.status] ?? this.status;
  }
}
