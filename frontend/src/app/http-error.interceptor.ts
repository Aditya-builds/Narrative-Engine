import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TimeoutError, catchError, retry, throwError, timeout, timer } from 'rxjs';
import { messageFromHttpError } from './http-error';
import { ToastService } from './toast.service';

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const millis = req.method === 'POST' && req.url.startsWith('/chat') ? 120_000 : 20_000;
  const retries = req.method === 'GET' ? 1 : 0;
  return next(req).pipe(
    timeout({ first: millis }),
    retry({
      count: retries,
      delay: (error, retryCount) => {
        const status = (error as { status?: number }).status;
        if (retryCount > retries || (status !== undefined && status !== 0 && status < 500)) {
          return throwError(() => error);
        }
        return timer(400);
      }
    }),
    catchError((err) => {
      const status = err?.status as number | undefined;
      const timedOut = err instanceof TimeoutError || err?.name === 'TimeoutError';
      if (timedOut || status === 0 || (typeof status === 'number' && status >= 500)) {
        toast.show(messageFromHttpError(err, 'Something went wrong. Try again in a moment.'));
      }
      return throwError(() => err);
    })
  );
};
