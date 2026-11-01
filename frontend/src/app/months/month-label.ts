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

/** "1st", "2nd", "3rd", "11th", "22nd" and so on. */
export function ordinal(day: number): string {
  const teens = day % 100 >= 11 && day % 100 <= 13;
  const suffix = teens
    ? 'th'
    : (({ 1: 'st', 2: 'nd', 3: 'rd' } as Record<number, string>)[day % 10] ?? 'th');
  return `${day}${suffix}`;
}

/** Payday: the last Thursday of a month, given its year and 0-based month index. */
function payday(year: number, monthIndex: number): Date {
  const lastDay = new Date(year, monthIndex + 1, 0);
  // Thursday is day 4; step back to it from the last day of the month.
  lastDay.setDate(lastDay.getDate() - ((lastDay.getDay() + 3) % 7));
  return lastDay;
}

/** The weeks from the previous month's payday to this month's: always 4 or 5. */
export function paydayWeeks(month: string): number {
  const [year, monthNumber] = month.split('-').map(Number);
  const days =
    (payday(year, monthNumber - 1).getTime() - payday(year, monthNumber - 2).getTime()) /
    86_400_000;
  return Math.round(days / 7);
}
