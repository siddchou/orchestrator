import { Component, Input, OnInit, inject } from '@angular/core';
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
    this.notificationService.getSubscriptionsForJob(this.jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.subscriptions = res.data;
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.error('Failed to load notifications', 'Dismiss');
      },
    });

    this.notificationService.listChannelSchemas().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.channelSchemas = res.data;
        }
      },
      error: () => {
        this.snackBar.error('Failed to load channel schemas', 'Dismiss');
      },
    });
  }

  openCreateDialog(): void {
    const data: SubscriptionFormDialogData = {
      jobId: this.jobId,
      mode: 'create',
      channelSchemas: this.channelSchemas,
    };
    this.dialog.open(NotificationSubscriptionFormComponent, { data }).afterClosed().subscribe(result => {
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
    this.dialog.open(NotificationSubscriptionFormComponent, { data }).afterClosed().subscribe(result => {
      if (result) this.load();
    });
  }

  confirmToggleSubscription(subscription: NotificationSubscription): void {
    const action = subscription.active ? 'Disable' : 'Enable';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: `${action} Notification`,
        message: `${action} the ${subscription.channelType} notification?`,
        confirmButton: action,
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.notificationService.toggleSubscription(subscription.id).subscribe({
        next: (res) => {
          if (res.status === 'SUCCESS') {
            subscription.active = res.data.active;
            this.snackBar.open(
              subscription.active ? 'Notification enabled' : 'Notification disabled',
              'Dismiss', { duration: 2000 }
            );
          }
        },
        error: () => {
          this.snackBar.error('Failed to toggle notification', 'Dismiss');
        },
      });
    });
  }

  deleteSubscription(subscription: NotificationSubscription): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Notification',
        message: `Delete the ${subscription.channelType} notification?`,
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
          }
        },
        error: () => {
          this.snackBar.error('Failed to delete notification', 'Dismiss');
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
}
