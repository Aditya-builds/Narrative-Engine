import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OpenAiKeyStore } from './openai-key.store';

export const openaiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/chat')) {
    return next(req);
  }
  const key = inject(OpenAiKeyStore).get();
  if (!key) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-OpenAI-Api-Key': key } }));
};
