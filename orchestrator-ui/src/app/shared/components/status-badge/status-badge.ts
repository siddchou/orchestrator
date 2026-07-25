import { Component, Input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { RunStatus } from '@app/core/models/job.model';

const STATUS_COLORS: Record<RunStatus, { foreground: string; background: string }> = {
  PENDING:   { foreground: '#ffffff', background: 'var(--status-pending-bg)' },
  RUNNING:   { foreground: '#ffffff', background: 'var(--status-running-bg)' },
  SUCCESS:   { foreground: '#ffffff', background: 'var(--status-success-bg)' },
  FAILED:    { foreground: '#ffffff', background: 'var(--status-failed-bg)' },
  PARTIAL:   { foreground: '#ffffff', background: 'var(--status-partial-bg)' },
  CANCELLED: { foreground: '#ffffff', background: 'var(--status-cancelled-bg)' },
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
  standalone: true,
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
