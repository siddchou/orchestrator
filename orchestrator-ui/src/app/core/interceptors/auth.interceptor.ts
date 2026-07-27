import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '@app/core/services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  const teamId = auth.getActiveTeamId?.();
  if (teamId != null && !req.url.includes('/api/teams')) {
    req = req.clone({
      setHeaders: { 'X-Team-Id': String(teamId) },
    });
  }

  return next(req);
};
