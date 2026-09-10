import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

/**
 * Starts the trace in the browser (NFR-6): the backend reuses this id rather
 * than generating its own, so one id covers the whole round trip.
 */
export const correlationIdInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiBaseUrl)) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { 'X-Correlation-Id': newCorrelationId() },
    }),
  );
};

function newCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `cid-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
