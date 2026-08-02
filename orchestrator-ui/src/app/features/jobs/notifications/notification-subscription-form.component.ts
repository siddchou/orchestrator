import { ChangeDetectorRef, Component, OnInit, ViewChild, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { DynamicConfigFormComponent } from '@app/shared/components/dynamic-config-form/dynamic-config-form';
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
    CommonModule, ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCheckboxModule, MatSnackBarModule,
    DynamicConfigFormComponent,
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
  private cd = inject(ChangeDetectorRef);
  data = inject<SubscriptionFormDialogData>(MAT_DIALOG_DATA);

  @ViewChild(DynamicConfigFormComponent) configForm?: DynamicConfigFormComponent;

  channelSchemas: ChannelConfigSchema[] = this.data.channelSchemas;
  credentials: Credential[] = [];

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
    });

    if (this.data.mode === 'edit' && this.data.subscription) {
      const sub = this.data.subscription!;
      const events = new Set(sub.events.split(',').filter(Boolean));
      this.selectedEvents = events;
      this.form.patchValue({ channelType: sub.channelType });
      this.selectedChannelSchema = this.channelSchemas.find(s => s.type === sub.channelType) ?? null;
    } else if (this.channelSchemas.length > 0) {
      this.form.patchValue({ channelType: this.channelSchemas[0].type });
    }

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

  save(): void {
    if (this.form.invalid || this.selectedEvents.size === 0) return;

    const result = this.configForm?.toConfig();
    if (result && !result.valid) {
      this.configForm.validate();
      return;
    }

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
        this.cd.markForCheck();
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
