import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { jwtInterceptor } from './core/auth/jwt.interceptor';
import { correlationIdInterceptor } from './core/http/correlation-id.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { servedByInterceptor } from './core/http/served-by.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    provideHttpClient(
      // Order matters: the correlation id is stamped first so it is present on
      // the request the error interceptor may later report against.
      withInterceptors([correlationIdInterceptor, jwtInterceptor, servedByInterceptor, errorInterceptor]),
    ),
  ],
};
