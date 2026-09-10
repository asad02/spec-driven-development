import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/** Attaches the bearer token to API calls, and only to API calls. */
export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken();

  const isApiCall = request.url.startsWith(environment.apiBaseUrl);
  const isLogin = request.url.endsWith('/auth/login');

  if (!token || !isApiCall || isLogin) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
