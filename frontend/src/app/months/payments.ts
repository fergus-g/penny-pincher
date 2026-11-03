// Payments: ticking AIB Expenses as paid in the current month (GLOSSARY "Payment", ADR 0003).

import { Entry } from './month.model';

/** "yyyy-MM" for a date, in local time. */
function monthOf(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** Whether `month` is the month `now` falls in. */
export function isCurrentMonth(month: string, now = new Date()): boolean {
  return month === monthOf(now);
}

/**
 * Whether the Entry can be ticked or unticked: an AIB Expense (the backend sends `paid` as null for
 * anything else), in the current month only.
 */
export function isTickable(entry: Entry, month: string, now = new Date()): boolean {
  return entry.paid !== null && isCurrentMonth(month, now);
}

/**
 * The Unpaid Expenses: AIB Expenses in the current month whose Due Day has arrived and that aren't
 * ticked yet, soonest Due Day first. Expenses with no Due Day are never Unpaid.
 */
export function unpaidExpenses(entries: Entry[], month: string, now = new Date()): Entry[] {
  if (!isCurrentMonth(month, now)) {
    return [];
  }
  return entries
    .filter(
      (entry) =>
        entry.paid === false &&
        entry.dueDayInMonth !== null &&
        entry.dueDayInMonth <= now.getDate(),
    )
    .sort((a, b) => a.dueDayInMonth! - b.dueDayInMonth! || a.id - b.id);
}
