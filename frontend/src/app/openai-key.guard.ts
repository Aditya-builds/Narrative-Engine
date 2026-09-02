import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OpenAiKeyStore } from './openai-key.store';

export const openaiKeyGuard: CanActivateFn = (_route, state) => {
  const keys = inject(OpenAiKeyStore);
  const router = inject(Router);
  if (keys.canChat()) {
    return true;
  }
  return router.createUrlTree(['/api-key'], { queryParams: { returnUrl: state.url } });
};
