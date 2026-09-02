import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { openaiKeyInterceptor } from './openai-key.interceptor';
import { requestIdInterceptor } from './request-id.interceptor';
import { httpErrorInterceptor } from './http-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([requestIdInterceptor, openaiKeyInterceptor, httpErrorInterceptor])),
    provideRouter(routes)
  ]
};
