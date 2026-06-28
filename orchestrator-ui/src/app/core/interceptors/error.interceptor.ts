import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { inject } from '@angular/core';
import { Router } from '@angular/router';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const router = inject(Router);

  return next(req).pipe(
    catchError(err => {
      if (err.status === 401 && !req.url.includes('/auth/login')) {
        localStorage.removeItem('authToken');
        localStorage.removeItem('authUser');
        router.navigate(['/login']);
        return throwError(() => err);
      }
      if (err.status !== 0) {
        const msg = err.error?.error || err.statusText || `HTTP error ${err.status}`;
        snackBar.open(msg, 'Dismiss', { duration: 5000, panelClass: 'error-snackbar' });
      }
      return throwError(() => err);
    })
  );
};
