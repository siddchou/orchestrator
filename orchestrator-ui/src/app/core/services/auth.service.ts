import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';

export interface AuthUser {
  token: string;
  role: string;
  passwordExpired: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly TOKEN_KEY = 'authToken';
  private readonly USER_KEY = 'authUser';

  private _currentUser = new BehaviorSubject<AuthUser | null>(this.loadStoredUser());
  currentUser = this._currentUser.asObservable();

  isLoggedIn(): boolean {
    return !!this._currentUser.value;
  }

  getUserRole(): string | null {
    return this._currentUser.value?.role ?? null;
  }

  isRole(role: string): boolean {
    return this.getUserRole()?.toUpperCase() === role.toUpperCase();
  }

  logout(): void {
    this._currentUser.next(null);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  private setSession(user: AuthUser): void {
    localStorage.setItem(this.TOKEN_KEY, user.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this._currentUser.next(user);
  }

  private loadStoredUser(): AuthUser | null {
    const userJson = localStorage.getItem(this.USER_KEY);
    if (!userJson) return null;
    try {
      return JSON.parse(userJson);
    } catch {
      return null;
    }
  }
}
