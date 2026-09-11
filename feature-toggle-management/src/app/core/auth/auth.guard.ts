import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Requires a session AND one of the two roles that may see the console. An
 * operator holding neither is sent back to login rather than shown an empty
 * console — they must not learn which features exist (functional spec FR-11).
 */
export const consoleGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() && auth.canRead()) {
    return true;
  }
  if (auth.isAuthenticated()) {
    auth.clearSession();
    return router.createUrlTree(['/login'], { queryParams: { reason: 'forbidden' } });
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
