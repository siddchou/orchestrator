import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { inject } from '@angular/core';
import { AuthService } from '@app/core/services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const snack = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: any) => {
      if (err.status === 401 && !req.url.includes('/auth/login')) {
        auth.logout();
      } else if (err.status >= 500) {
        snack.open('Server error — please try again', 'Dismiss', { duration: 4000 });
      }
      return throwError(() => err);
    })
  );
};
