import { HttpErrorResponse } from '@angular/common/http';

/** Turns a backend ProblemDetail (with per-field `errors`) into one line of text. */
export function errorMessage(error: HttpErrorResponse, fallback: string): string {
  const fieldErrors: Record<string, string> | undefined = error.error?.errors;
  if (fieldErrors) {
    return Object.entries(fieldErrors)
      .map(([field, message]) => `${field} ${message}`)
      .join(', ');
  }
  return error.error?.detail ?? fallback;
}
