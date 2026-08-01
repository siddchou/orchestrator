import { FieldDefinition } from '@app/core/models/job.model';

export type NotificationEventName = 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED';

export interface ChannelConfigSchema {
  type: string;
  fields: FieldDefinition[];
}

export interface NotificationSubscription {
  id: number;
  jobId: number;
  channelType: string;
  events: string; // comma-separated event names
  config?: Record<string, unknown>;
  active: boolean;
  createdAt: string;
}

export interface NotificationSubscriptionRequest {
  jobId: number;
  channelType: string;
  events: string[];
  config: Record<string, unknown>;
}

export interface NotificationDeliveryLog {
  id: number;
  subscriptionId: number;
  runId: number;
  channelType: string;
  status: string; // 'PENDING' | 'SENT' | 'FAILED'
  attemptCount: number;
  errorMessage: string | null;
  createdAt: string;
  sentAt: string | null;
}
