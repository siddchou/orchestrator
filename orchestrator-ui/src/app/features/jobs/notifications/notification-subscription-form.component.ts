import { Component, OnInit, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { DynamicFieldComponent } from '@app/shared/components/dynamic-field/dynamic-field';
import { NotificationService } from '@app/core/services/notification.service';
import { ChannelConfigSchema, NotificationEventName, NotificationSubscription, NotificationSubscriptionRequest } from '@app/core/models/notification.model';

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
    MatInputModule, MatSelectModule, MatButtonModule, MatCheckboxModule,
    DynamicFieldComponent,
  ],
  templateUrl: './notification-subscription-form.component.html',
  styleUrl: './notification-subscription-form.component.scss',
})
export class NotificationSubscriptionFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private notificationService = inject(NotificationService);
  private dialogRef = inject(MatDialogRef<NotificationSubscription>);
  data = inject<SubscriptionFormDialogData>(MAT_DIALOG_DATA);

  channelSchemas: ChannelConfigSchema[] = this.data.channelSchemas;

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
      configFields: this.fb.group({}),
    });

    this.form.get('channelType')!.valueChanges.subscribe(type => {
      this.selectedChannelSchema = this.channelSchemas.find(s => s.type === type) ?? null;
      this.buildConfigFields();
    });

    if (this.data.mode === 'edit' && this.data.subscription) {
      const sub = this.data.subscription!;
      const events = new Set(sub.events.split(',').filter(Boolean));
      this.selectedEvents = events;
      this.form.patchValue({ channelType: sub.channelType });
      this.selectedChannelSchema = this.channelSchemas.find(s => s.type === sub.channelType) ?? null;
      this.buildConfigFields();
    } else if (this.channelSchemas.length > 0) {
      this.form.patchValue({ channelType: this.channelSchemas[0].type });
    }
  }

  private buildConfigFields(): void {
    const fields = this.selectedChannelSchema?.fields ?? [];
    const configGroup = this.fb.group({});
    for (const field of fields) {
      const init = field.defaultValue != null ? field.defaultValue : '';
      configGroup.addControl(field.name, this.fb.control(init, field.required ? Validators.required : []));
    }
    this.form.setControl('configFields', configGroup);
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
    const val = this.form.value;
    const config: Record<string, unknown> = {};
    const fields = this.selectedChannelSchema?.fields ?? [];
    for (const field of fields) {
      let value = val.configFields?.[field.name];
      if (field.type === 'LIST_STRING' && typeof value === 'string') {
        value = (value as string).split(',').map((s: string) => s.trim()).filter(Boolean);
      }
      config[field.name] = value;
    }

    const request: NotificationSubscriptionRequest = {
      jobId: this.data.jobId,
      channelType: val.channelType,
      events: Array.from(this.selectedEvents),
      config,
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
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
