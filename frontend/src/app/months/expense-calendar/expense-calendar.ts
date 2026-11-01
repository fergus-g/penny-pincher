import { CurrencyPipe } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';

import { ordinal, toDate } from '../month-label';
import { accountLabel, Entry } from '../month.model';

const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });

/** Monday first, as Irish calendars are. */
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

interface Day {
  day: number;
  /** The month's Expenses that go out on this day. */
  expenses: Entry[];
  total: number;
  today: boolean;
}

/**
 * The month as a calendar of Expense Due Days: each day with Expenses due shows a dot and its
 * total. Hovering, focusing or tapping a day lists its Expenses; clicking one asks to edit it.
 * Expenses with no Due Day don't appear.
 */
@Component({
  imports: [CurrencyPipe],
  selector: 'app-expense-calendar',
  styleUrl: './expense-calendar.scss',
  templateUrl: './expense-calendar.html',
})
export class ExpenseCalendar {
  /** The month being viewed, "yyyy-MM". */
  readonly month = input.required<string>();
  /** The month's Entries, each with its Due Day as it falls in this month. */
  readonly entries = input.required<Entry[]>();
  /** An Expense picked from a day's list. */
  readonly edit = output<Entry>();

  protected readonly weekdays = WEEKDAYS;
  protected readonly accountLabel = accountLabel;

  /** Blank cells before the 1st, so it lands under its weekday. */
  protected readonly leading = computed(() => Array((toDate(this.month()).getDay() + 6) % 7));

  protected readonly days = computed<Day[]>(() => {
    const first = toDate(this.month());
    const length = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const now = new Date();
    const thisMonth =
      now.getFullYear() === first.getFullYear() && now.getMonth() === first.getMonth();
    return Array.from({ length }, (_, index) => {
      const day = index + 1;
      const expenses = this.entries().filter(
        (entry) => entry.type === 'EXPENSE' && entry.dueDayInMonth === day,
      );
      return {
        day,
        expenses,
        total: expenses.reduce((sum, entry) => sum + entry.amount, 0),
        today: thisMonth && now.getDate() === day,
      };
    });
  });

  /** The day whose Expenses are listed: the one hovered, focused or tapped last. */
  protected readonly selectedDay = signal<number | null>(null);
  protected readonly selected = computed(() => {
    const day = this.selectedDay();
    return (
      this.days().find((candidate) => candidate.day === day && candidate.expenses.length) ?? null
    );
  });

  protected select(day: number): void {
    this.selectedDay.set(day);
  }

  protected dayLabel(day: Day): string {
    const date = new Date(toDate(this.month()));
    date.setDate(day.day);
    const weekday = date.toLocaleString('en', { weekday: 'long' });
    const count = `${day.expenses.length} ${day.expenses.length === 1 ? 'Expense' : 'Expenses'}`;
    return `${weekday} ${ordinal(day.day)}: ${count}, ${euros.format(day.total)}`;
  }

  protected readonly ordinal = ordinal;
}
