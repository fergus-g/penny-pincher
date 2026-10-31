import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs';

/** Logs every HTTP request, and its response or error, with the time taken. */
export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const label = `${req.method} ${req.urlWithParams}`;
  const started = performance.now();
  const elapsed = () => `${Math.round(performance.now() - started)} ms`;
  console.log(`[HTTP] → ${label}`);
  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          console.log(`[HTTP] ← ${event.status} ${label} (${elapsed()})`, event.body);
        }
      },
      error: (error: HttpErrorResponse) => {
        console.error(`[HTTP] ✗ ${error.status} ${label} (${elapsed()})`, error.error);
      },
    }),
  );
};
