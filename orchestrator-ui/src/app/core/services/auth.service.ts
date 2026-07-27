import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, tap } from 'rxjs';
import { Router } from '@angular/router';
import { ApiResponse, TeamSummary } from '@app/core/models/api-response.model';

export interface AuthUser {
  token: string;
  role: string;
  passwordExpired: boolean;
  teams?: TeamSummary[];
  activeTeamId?: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly STORAGE_KEY = 'orch_auth';

  // Session storage survives page reloads but clears when browser closes
  private _currentUser = new BehaviorSubject<AuthUser | null>(this.loadFromStorage());
  currentUser = this._currentUser.asObservable();

  private saveToStorage(user: AuthUser | null): void {
    if (user) {
      sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(this.STORAGE_KEY);
    }
  }

  private loadFromStorage(): AuthUser | null {
    try {
      const raw = sessionStorage.getItem(this.STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw) as AuthUser;
      }
    } catch {
      // corrupted data — clear
    }
    return null;
  }

  login(username: string, password: string) {
    return this.http.post<ApiResponse<{ accessToken: string; expiresInSeconds: number; role: string; passwordExpired: boolean }>>(
      '/api/auth/login',
      { username, password }
    ).pipe(
      tap(resp => {
        if (resp.status === 'SUCCESS' && resp.data) {
          const user: AuthUser = {
            token: resp.data.accessToken,
            role: resp.data.role,
            passwordExpired: resp.data.passwordExpired,
          };
          this._currentUser.next(user);
          this.saveToStorage(user);
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

  getTeams(): TeamSummary[] {
    return this._currentUser.value?.teams ?? [];
  }

  getActiveTeamId(): number | null {
    return this._currentUser.value?.activeTeamId ?? null;
  }

  setActiveTeamId(teamId: number): void {
    const user = this._currentUser.value;
    if (user) {
      user.activeTeamId = teamId;
      this._currentUser.next(user);
      this.saveToStorage(user);
    }
  }

  loadTeams(teams: TeamSummary[], activeTeamId?: number): void {
    const user = this._currentUser.value;
    if (user) {
      user.teams = teams;
      if (activeTeamId) {
        user.activeTeamId = activeTeamId;
      } else if (teams.length === 1) {
        user.activeTeamId = teams[0].teamId;
      }
      this._currentUser.next(user);
      this.saveToStorage(user);
    }
  }

  logout(): void {
    this._currentUser.next(null);
    this.saveToStorage(null);
    this.router.navigate(['/login']);
  }
}
