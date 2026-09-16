import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ApiError, AppError } from '../models/api-error.model';

/** Normalises every failure into one shape the components can render. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/login')) {
        auth.clearSession();
        router.navigate(['/login'], {
          replaceUrl: true,
          queryParams: { returnUrl: router.url, reason: 'expired' },
        });
      }

      const body = error.error as ApiError | null;
      const appError: AppError = {
        status: error.status,
        code: body?.code ?? (error.status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN'),
        message:
          body?.message ??
          (error.status === 0
            ? 'Could not reach the server.'
            : 'Something went wrong. Please try again.'),
        correlationId: body?.correlationId,
        fieldErrors: body?.fieldErrors,
      };
      return throwError(() => appError);
    }),
  );
};
