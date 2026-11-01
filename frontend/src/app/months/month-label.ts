// Months are "yyyy-MM" strings, e.g. "2026-11".

/** The first day of the month, in local time. */
export function toDate(month: string): Date {
  const [year, monthNumber] = month.split('-').map(Number);
  return new Date(year, monthNumber - 1, 1);
}

/** "November 2026", or "Nov 2026" when short. */
export function monthLabel(month: string, length: 'long' | 'short' = 'long'): string {
  return toDate(month).toLocaleString('en', { month: length, year: 'numeric' });
}
