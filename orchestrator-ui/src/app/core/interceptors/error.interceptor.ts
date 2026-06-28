import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { inject } from '@angular/core';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError(err => {
      if (err.status !== 0) {
        const msg = err.error?.error || err.statusText || `HTTP error ${err.status}`;
        snackBar.open(msg, 'Dismiss', { duration: 5000, panelClass: 'error-snackbar' });
      }
      return throwError(() => err);
    })
  );
};
