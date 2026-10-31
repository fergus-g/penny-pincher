import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { MonthApi } from '../month-api';
import { Entry } from '../month.model';

/** The current month as "yyyy-MM". */
export function currentMonth(): string {
  return formatMonth(new Date());
}

function formatMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function toDate(month: string): Date {
  const [year, monthNumber] = month.split('-').map(Number);
  return new Date(year, monthNumber - 1, 1);
}

function shiftMonth(month: string, delta: number): string {
  const date = toDate(month);
  date.setMonth(date.getMonth() + delta);
  return formatMonth(date);
}

@Component({
  imports: [CurrencyPipe],
  selector: 'app-month-page',
  styleUrl: './month-page.scss',
  templateUrl: './month-page.html',
})
export class MonthPage {
  private readonly monthApi = inject(MonthApi);
  private readonly router = inject(Router);

  /** Bound from the `:month` route parameter, e.g. "2026-10". */
  readonly month = input.required<string>();

  protected readonly label = computed(() =>
    toDate(this.month()).toLocaleString('en', { month: 'long', year: 'numeric' }),
  );

  /** Refetches whenever the month changes. */
  protected readonly summary = rxResource({
    params: () => this.month(),
    stream: ({ params }) => this.monthApi.getMonth(params),
  });

  private readonly entries = computed(() => (this.summary.hasValue() ? this.summary.value().entries : []));
  protected readonly incomes = computed(() => this.entries().filter((entry) => entry.type === 'INCOME'));
  protected readonly expenses = computed(() => this.entries().filter((entry) => entry.type === 'EXPENSE'));

  protected previous(): void {
    const target = shiftMonth(this.month(), -1);
    console.log('[MonthPage] previous clicked →', target);
    this.router.navigate(['/months', target]);
  }

  protected next(): void {
    const target = shiftMonth(this.month(), 1);
    console.log('[MonthPage] next clicked →', target);
    this.router.navigate(['/months', target]);
  }

  protected kind(entry: Entry): string {
    if (entry.endMonth === null) {
      return 'Recurring';
    }
    return entry.endMonth === entry.startMonth ? 'One-off' : `Until ${entry.endMonth}`;
  }
}
