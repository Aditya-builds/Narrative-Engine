import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OpenAiKeyStore } from './openai-key.store';

export const openaiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/chat') || req.method !== 'POST') {
    return next(req);
  }
  const keys = inject(OpenAiKeyStore);
  if (keys.source() !== 'user') {
    return next(req);
  }
  const key = keys.get();
  if (!key) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-OpenAI-Api-Key': key } }));
};
