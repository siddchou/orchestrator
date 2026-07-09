import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import {
  Credential,
  KeyGenerationRequest,
  KeyGenerationResponse
} from '../../features/credentials/credential.model';

@Injectable({
  providedIn: 'root'
})
export class CredentialService {
  private http = inject(HttpClient);
  private api = '/api';

  listCredentials() {
    return this.http.get<{ status: string; data: Credential[] }>(`${this.api}/credentials`)
      .pipe(catchError(this.handleError));
  }

  createCredential(ref: string, type: string, value: string) {
    return this.http.post<{ status: string; data: Credential }>(`${this.api}/credentials`, {
      ref,
      type,
      value
    }).pipe(catchError(this.handleError));
  }

  generateKeys(request: KeyGenerationRequest) {
    return this.http.post<{ status: string; data: KeyGenerationResponse }>(
      `${this.api}/credentials/generate-keys`,
      request
    ).pipe(catchError(this.handleError));
  }

  deleteCredential(id: number) {
    return this.http.delete(`${this.api}/credentials/${id}`)
      .pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'An unknown error occurred';
    if (error.error instanceof ErrorEvent) {
      errorMessage = `Error: ${error.error.message}`;
    } else {
      errorMessage = `Error: ${error.status} - ${error.statusText}`;
      if (error.error?.message) {
        errorMessage += ` - ${error.error.message}`;
      }
    }
    return throwError(() => new Error(errorMessage));
  }
}
