import { ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { NotificationService } from '@app/core/services/notification.service';
import { ConfirmDialog } from '@app/shared/components/confirm-dialog/confirm-dialog';
import { ChannelConfigSchema, NotificationSubscription } from '@app/core/models/notification.model';
import { DeliveryLogComponent } from './delivery-log.component';
import { NotificationSubscriptionFormComponent, SubscriptionFormDialogData } from './notification-subscription-form.component';

@Component({
  selector: 'app-notifications-tab',
  imports: [
    CommonModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule,
    MatTooltipModule, MatDialogModule, MatSnackBarModule, MatProgressSpinnerModule,
    MatExpansionModule, DeliveryLogComponent,
  ],
  templateUrl: './notifications-tab.component.html',
  styleUrl: './notifications-tab.component.scss',
})
export class NotificationsTabComponent implements OnInit {
  private notificationService = inject(NotificationService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private cd = inject(ChangeDetectorRef);

  @Input() jobId!: number;

  subscriptions: NotificationSubscription[] = [];
  channelSchemas: ChannelConfigSchema[] = [];
  loading = true;
  expandedSubscriptionId: number | null = null;

  displayedColumns = ['channelType', 'events', 'active', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.cd.markForCheck();
    this.notificationService.getSubscriptionsForJob(this.jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.subscriptions = res.data;
        }
        this.loading = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Failed to load notifications', 'Dismiss', { panelClass: 'error-snackbar' });
        this.cd.markForCheck();
      },
    });

    this.notificationService.listChannelSchemas().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.channelSchemas = res.data;
        }
        this.cd.markForCheck();
      },
      error: () => {
        this.snackBar.open('Failed to load channel schemas', 'Dismiss', { panelClass: 'error-snackbar' });
      },
    });
  }

  openCreateDialog(): void {
    const data: SubscriptionFormDialogData = {
      jobId: this.jobId,
      mode: 'create',
      channelSchemas: this.channelSchemas,
    };
    this.dialog.open(NotificationSubscriptionFormComponent, { data, width: '520px' }).afterClosed().subscribe(result => {
      if (result) this.load();
    });
  }

  openEditDialog(subscription: NotificationSubscription): void {
    const data: SubscriptionFormDialogData = {
      jobId: this.jobId,
      mode: 'edit',
      subscription,
      channelSchemas: this.channelSchemas,
    };
    this.dialog.open(NotificationSubscriptionFormComponent, { data, width: '520px' }).afterClosed().subscribe(result => {
      if (result) this.load();
    });
  }

  confirmToggleSubscription(subscription: NotificationSubscription): void {
    const action = subscription.active ? 'Disable' : 'Enable';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: `${action} Notification`,
        message: `${action} the ${this.typeToLabel(subscription.channelType)} notification?`,
        confirmButton: action,
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.notificationService.toggleSubscription(subscription.id).subscribe({
        next: (res) => {
          if (res.status === 'SUCCESS') {
            const newActive = res.data.active;
            this.subscriptions = this.subscriptions.map(s => s.id === subscription.id ? { ...s, active: newActive } : s);
            this.snackBar.open(
              newActive ? 'Notification enabled' : 'Notification disabled',
              'Dismiss', { duration: 2000 }
            );
            this.cd.markForCheck();
          }
        },
        error: () => {
          this.snackBar.open('Failed to toggle notification', 'Dismiss', { panelClass: 'error-snackbar' });
        },
      });
    });
  }

  deleteSubscription(subscription: NotificationSubscription): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Notification',
        message: `Delete the ${this.typeToLabel(subscription.channelType)} notification?`,
        confirmButton: 'Delete',
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.notificationService.deleteSubscription(subscription.id).subscribe({
        next: (res) => {
          if (res.status === 'SUCCESS') {
            this.subscriptions = this.subscriptions.filter(s => s.id !== subscription.id);
            if (this.expandedSubscriptionId === subscription.id) {
              this.expandedSubscriptionId = null;
            }
            this.snackBar.open('Notification deleted', 'Dismiss', { duration: 2000 });
            this.cd.markForCheck();
          }
        },
        error: () => {
          this.snackBar.open('Failed to delete notification', 'Dismiss', { panelClass: 'error-snackbar' });
        },
      });
    });
  }

  toggleDeliveryLog(subscriptionId: number): void {
    this.expandedSubscriptionId = this.expandedSubscriptionId === subscriptionId ? null : subscriptionId;
  }

  getEventChips(events: string): string[] {
    return events.split(',').filter(Boolean);
  }

  getEventColor(event: string): string {
    if (event === 'SUCCESS') return '#4caf50';
    if (event === 'FAILED') return '#f44336';
    if (event === 'PARTIAL') return '#ff9800';
    return '#9e9e9e'; // CANCELLED
  }

  typeToLabel(type: string): string {
    const labels: Record<string, string> = {
      EMAIL: 'Email',
      SLACK_WEBHOOK: 'Slack Webhook',
      GENERIC_WEBHOOK: 'Generic Webhook',
    };
    return labels[type] ?? type;
  }
}
