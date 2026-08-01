import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { NotificationService } from '@app/core/services/notification.service';
import { NotificationDeliveryLog } from '@app/core/models/notification.model';

@Component({
  selector: 'app-delivery-log',
  imports: [CommonModule, MatTableModule, MatIconModule, MatChipsModule, MatProgressSpinnerModule, MatSnackBarModule],
  templateUrl: './delivery-log.component.html',
  styleUrl: './delivery-log.component.scss',
})
export class DeliveryLogComponent implements OnInit {
  private notificationService = inject(NotificationService);
  private snackBar = inject(MatSnackBar);

  @Input() subscriptionId: number | null = null;
  @Input() runId: number | null = null;

  logs: NotificationDeliveryLog[] = [];
  loading = true;

  displayedColumns = ['status', 'channelType', 'runId', 'attempts', 'error', 'sentAt'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.notificationService.getDeliveryLog(this.subscriptionId ?? undefined, this.runId ?? undefined).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.logs = res.data.slice(0, 20); // show last 20
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.error('Failed to load delivery log', 'Dismiss');
      },
    });
  }

  getStatusColor(status: string): string {
    if (status === 'SENT') return '#4caf50';
    if (status === 'FAILED') return '#f44336';
    return '#ff9800'; // PENDING or unknown
  }
}
