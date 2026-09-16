import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { FeatureService } from '../features/feature.service';

/**
 * Records the X-Served-By header every backend stamps on its responses, so the UI
 * reports which implementation actually handled the request rather than which one
 * the flag claims is active.
 */
export const servedByInterceptor: HttpInterceptorFn = (req, next) => {
  const features = inject(FeatureService);

  // The feature endpoints are pinned to feature-toggle-management-service in nginx
  // regardless of routing, so their X-Served-By says nothing about which backend
  // serves data. Recording it would make the indicator permanently wrong.
  const isFeatureEndpoint = req.url.includes('/features');

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse && !isFeatureEndpoint) {
        features.recordServedBy(event.headers.get('X-Served-By'));
      }
    }),
  );
};
