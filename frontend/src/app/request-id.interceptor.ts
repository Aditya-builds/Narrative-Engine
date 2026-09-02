import { HttpInterceptorFn } from '@angular/common/http';
import { newRequestId } from './request-id';

export const requestIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.headers.has('X-Request-ID')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-Request-ID': newRequestId() } }));
};
