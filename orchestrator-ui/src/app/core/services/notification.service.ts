import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '@app/core/models/api-response.model';
import {
  ChannelConfigSchema,
  NotificationDeliveryLog,
  NotificationSubscription,
  NotificationSubscriptionRequest,
} from '@app/core/models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private api = '/api/notifications';

  listChannelSchemas(): Observable<ApiResponse<ChannelConfigSchema[]>> {
    return this.http.get<ApiResponse<ChannelConfigSchema[]>>(`${this.api}/channels`);
  }

  getSubscriptionsForJob(jobId: number): Observable<ApiResponse<NotificationSubscription[]>> {
    return this.http.get<ApiResponse<NotificationSubscription[]>>(`${this.api}/subscriptions/job/${jobId}`);
  }

  createSubscription(request: NotificationSubscriptionRequest): Observable<ApiResponse<NotificationSubscription>> {
    return this.http.post<ApiResponse<NotificationSubscription>>(`${this.api}/subscriptions`, request);
  }

  updateSubscription(id: number, request: NotificationSubscriptionRequest): Observable<ApiResponse<NotificationSubscription>> {
    return this.http.put<ApiResponse<NotificationSubscription>>(`${this.api}/subscriptions/${id}`, request);
  }

  deleteSubscription(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.api}/subscriptions/${id}`);
  }

  toggleSubscription(id: number): Observable<ApiResponse<NotificationSubscription>> {
    return this.http.patch<ApiResponse<NotificationSubscription>>(`${this.api}/subscriptions/${id}/toggle`, {});
  }

  getDeliveryLog(subscriptionId?: number, runId?: number): Observable<ApiResponse<NotificationDeliveryLog[]>> {
    let params = new HttpParams();
    if (subscriptionId !== undefined) params = params.set('subscriptionId', String(subscriptionId));
    if (runId !== undefined) params = params.set('runId', String(runId));
    return this.http.get<ApiResponse<NotificationDeliveryLog[]>>(`${this.api}/delivery-log`, { params });
  }
}
