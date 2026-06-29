import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LogStreamService {
  streamLog(runId: number): Observable<string> {
    return new Observable<string>(observer => {
      const source = new EventSource(`/api/runs/${runId}/log-stream`);

      source.onmessage = (event: MessageEvent) => {
        observer.next(event.data);
      };

      source.addEventListener('done', () => {
        observer.complete();
        source.close();
      });

      source.onerror = (err) => {
        observer.error(err);
        source.close();
      };

      return () => source.close();
    });
  }
}
