import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '@app/core/services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  const teamId = auth.getActiveTeamId?.();

  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (teamId != null && !req.url.includes('/api/teams')) {
    headers['X-Team-Id'] = String(teamId);
  }

  if (Object.keys(headers).length > 0) {
    req = req.clone({ setHeaders: headers });
  }

  // Debug logging for auth issues — remove after fixing
  if (!token) {
    console.warn('[authInterceptor] NO TOKEN for', req.method, req.url, '— isLoggedIn:', auth.isLoggedIn());
  }

  return next(req);
};
