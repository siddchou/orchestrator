import { ChangeDetectorRef, Component, OnInit, ViewChild, inject } from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DynamicConfigFormComponent } from '@app/shared/components/dynamic-config-form/dynamic-config-form';
import { ConfirmDialog } from '@app/shared/components/confirm-dialog/confirm-dialog';
import { NotificationService } from '@app/core/services/notification.service';
import { CredentialService } from '@app/core/services/credential.service';
import { ChannelConfigSchema, NotificationEventName, NotificationSubscription, NotificationSubscriptionRequest } from '@app/core/models/notification.model';
import { Credential } from '@app/core/models/credential.model';

const ALL_EVENTS: NotificationEventName[] = ['SUCCESS', 'FAILED', 'PARTIAL', 'CANCELLED'];

export interface SubscriptionFormDialogData {
  jobId: number;
  mode: 'create' | 'edit';
  subscription?: NotificationSubscription;
  channelSchemas: ChannelConfigSchema[];
}

@Component({
  selector: 'app-notification-subscription-form',
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, DynamicConfigFormComponent,
  ],
  templateUrl: './notification-subscription-form.component.html',
  styleUrl: './notification-subscription-form.component.scss',
})
export class NotificationSubscriptionFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private notificationService = inject(NotificationService);
  private credentialService = inject(CredentialService);
  private snackBar = inject(MatSnackBar);
  private dialogRef = inject(MatDialogRef<NotificationSubscription>);
  private dialog = inject(MatDialog);
  private cd = inject(ChangeDetectorRef);
  data = inject<SubscriptionFormDialogData>(MAT_DIALOG_DATA);

  @ViewChild(DynamicConfigFormComponent) configForm?: DynamicConfigFormComponent;

  channelSchemas: ChannelConfigSchema[] = this.data.channelSchemas;
  credentials: Credential[] = [];

  // Fix 2: saving state
  saving = false;
  // Fix 4: dynamic header icon
  headerIcon = 'notifications_active';
  // Fix 5: dirty check - snapshot of initial events
  private initialEventsSnapshot = new Set<string>();

  static typeToLabel(type: string): string {
    const labels: Record<string, string> = {
      EMAIL: 'Email',
      SLACK_WEBHOOK: 'Slack Webhook',
      GENERIC_WEBHOOK: 'Generic Webhook',
    };
    return labels[type] ?? type;
  }

  typeToLabel(type: string): string {
    return NotificationSubscriptionFormComponent.typeToLabel(type);
  }

  form!: FormGroup;
  selectedChannelSchema: ChannelConfigSchema | null = null;
  selectedEvents: Set<string> = new Set(ALL_EVENTS.slice(0, 2)); // default: SUCCESS + FAILED
  eventOptions: string[] = ALL_EVENTS;

  ngOnInit(): void {
    this.form = this.fb.group({
      channelType: ['', Validators.required],
    });

    this.form.get('channelType')!.valueChanges.subscribe(type => {
      this.selectedChannelSchema = this.channelSchemas.find(s => s.type === type) ?? null;
      // Fix 4: update header icon reactively
      this.headerIcon = this.typeToIcon(type);
    });

    if (this.data.mode === 'edit' && this.data.subscription) {
      const sub = this.data.subscription!;
      const events = new Set(sub.events.split(',').filter(Boolean));
      this.selectedEvents = events;
      this.form.patchValue({ channelType: sub.channelType });
      this.selectedChannelSchema = this.channelSchemas.find(s => s.type === sub.channelType) ?? null;
      // Fix 4: set icon for edit mode too
      this.headerIcon = this.typeToIcon(sub.channelType);
    } else if (this.channelSchemas.length > 0) {
      this.form.patchValue({ channelType: this.channelSchemas[0].type });
      // Fix 4: set icon for default channel
      this.headerIcon = this.typeToIcon(this.channelSchemas[0].type);
    }

    // Fix 5: capture initial events snapshot after form is populated
    this.initialEventsSnapshot = new Set(this.selectedEvents);

    // Load credentials for SECRET_REF fields
    this.credentialService.listCredentials().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') this.credentials = res.data;
        this.cd.markForCheck();
      },
      error: () => { /* non-critical — SECRET_REF fields will fall back to text input */ },
    });
  }

  onEventCheckboxChange(event: string, checked: boolean): void {
    if (checked) {
      this.selectedEvents.add(event);
    } else {
      this.selectedEvents.delete(event);
    }
  }

  toggleEvent(event: string): void {
    if (this.selectedEvents.has(event)) {
      this.selectedEvents.delete(event);
    } else {
      this.selectedEvents.add(event);
    }
  }

  eventToLabel(event: string): string {
    const labels: Record<string, string> = {
      SUCCESS: 'Success',
      FAILED: 'Failed',
      PARTIAL: 'Partial',
      CANCELLED: 'Cancelled',
    };
    return labels[event] ?? event;
  }

  getEventIcon(event: string): string {
    const icons: Record<string, string> = {
      SUCCESS: 'check_circle',
      FAILED: 'error',
      PARTIAL: 'remove_circle',
      CANCELLED: 'cancel',
    };
    return icons[event] ?? 'event';
  }

  getEventAccentColor(event: string): string {
    const colors: Record<string, string> = {
      SUCCESS: '#16a34a',
      FAILED: '#dc2626',
      PARTIAL: '#ea580c',
      CANCELLED: '#6b7280',
    };
    return colors[event] ?? '';
  }

  typeToIcon(type: string): string {
    const icons: Record<string, string> = {
      EMAIL: 'mail',
      SLACK_WEBHOOK: 'chat',
      GENERIC_WEBHOOK: 'link',
    };
    return icons[type] ?? 'notifications_active';
  }

  // Fix 5: check if form has dirty changes
  isDirty(): boolean {
    if (this.form.dirty) return true;
    // Compare current events set with initial snapshot
    if (this.selectedEvents.size !== this.initialEventsSnapshot.size) return true;
    for (const evt of this.selectedEvents) {
      if (!this.initialEventsSnapshot.has(evt)) return true;
    }
    return false;
  }

  save(): void {
    if (this.form.invalid || this.selectedEvents.size === 0) return;

    const result = this.configForm?.toConfig();
    if (result && !result.valid) {
      this.configForm.validate();
      return;
    }

    // Fix 2: set saving state
    this.saving = true;
    this.cd.markForCheck();

    const request: NotificationSubscriptionRequest = {
      jobId: this.data.jobId,
      channelType: this.form.value.channelType,
      events: Array.from(this.selectedEvents),
      config: result?.config ?? {},
    };

    const obs$ = this.data.mode === 'edit' && this.data.subscription
      ? this.notificationService.updateSubscription(this.data.subscription.id, request)
      : this.notificationService.createSubscription(request);

    obs$.subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.dialogRef.close(res.data);
        }
      },
      error: () => {
        this.snackBar.open(
          this.data.mode === 'edit' ? 'Failed to update notification' : 'Failed to create notification',
          'Dismiss', { panelClass: 'error-snackbar' }
        );
        // Fix 2: reset saving state on error
        this.saving = false;
        this.cd.markForCheck();
      },
    });
  }

  cancel(): void {
    // Fix 5: dirty check before closing
    if (this.isDirty()) {
      this.dialog.open(ConfirmDialog, {
        data: {
          title: 'Discard Changes',
          message: 'You have unsaved changes. Discard them?',
          confirmButton: 'Discard',
        },
        disableClose: false,
      }).afterClosed().subscribe(confirmed => {
        if (confirmed) {
          this.dialogRef.close();
        }
      });
    } else {
      this.dialogRef.close();
    }
  }
}
