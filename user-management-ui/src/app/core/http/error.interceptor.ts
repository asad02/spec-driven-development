import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ApiError, AppError } from '../models/api-error.model';

/**
 * Turns every backend failure into something a person can read, and handles the
 * one case components should never have to: an expired token (FR-7).
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      const isLogin = request.url.endsWith('/auth/login');

      if (response.status === 401 && !isLogin) {
        // clearSession, not logout: this handler does its own navigation so it can
        // carry returnUrl and the expiry reason.
        auth.clearSession();
        router.navigate(['/login'], {
          replaceUrl: true,
          queryParams: { returnUrl: router.url, reason: 'expired' },
        });
      }

      return throwError(() => toAppError(response));
    }),
  );
};

function toAppError(response: HttpErrorResponse): AppError {
  const body = response.error as ApiError | null;

  if (body && typeof body === 'object' && typeof body.code === 'string') {
    return {
      status: response.status,
      code: body.code,
      message: body.message,
      correlationId: body.correlationId,
      fieldErrors: body.fieldErrors ?? [],
    };
  }

  return {
    status: response.status,
    code: 'NETWORK_ERROR',
    message:
      response.status === 0
        ? 'Cannot reach the server. Check your connection and try again.'
        : 'Something went wrong. Please try again.',
    fieldErrors: [],
  };
}
