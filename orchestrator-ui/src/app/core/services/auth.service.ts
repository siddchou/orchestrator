import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, tap } from 'rxjs';
import { Router } from '@angular/router';
import { ApiResponse } from '../models/api-response.model';

export interface AuthUser {
  token: string;
  role: string;
  passwordExpired: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // In-memory token storage (not localStorage) to protect against XSS
  private _currentUser = new BehaviorSubject<AuthUser | null>(null);
  currentUser = this._currentUser.asObservable();

  login(username: string, password: string) {
    return this.http.post<ApiResponse<{ accessToken: string; expiresInSeconds: number; role: string; passwordExpired: boolean }>>(
      '/api/auth/login',
      { username, password }
    ).pipe(
      tap(resp => {
        if (resp.status === 'SUCCESS' && resp.data) {
          this._currentUser.next({
            token: resp.data.accessToken,
            role: resp.data.role,
            passwordExpired: resp.data.passwordExpired,
          });
        }
      }),
      map(() => void 0)
    );
  }

  isLoggedIn(): boolean {
    return !!this._currentUser.value;
  }

  getToken(): string | null {
    return this._currentUser.value?.token ?? null;
  }

  getUserRole(): string | null {
    return this._currentUser.value?.role ?? null;
  }

  isRole(role: string): boolean {
    return this.getUserRole()?.toUpperCase() === role.toUpperCase();
  }

  logout(): void {
    this._currentUser.next(null);
    this.router.navigate(['/login']);
  }
}
