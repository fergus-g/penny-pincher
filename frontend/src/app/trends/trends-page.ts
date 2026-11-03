import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { forkJoin } from 'rxjs';

import { COLOURS } from '../months/expense-chart/expense-chart';
import { MonthApi } from '../months/month-api';
import { monthLabel, toDate } from '../months/month-label';
import { isDebt, MonthSummary } from '../months/month.model';
import { currentMonth } from '../months/month-page/month-page';

/** How many months the page covers, ending with the current one. */
export const TREND_MONTHS = 12;

const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });
const roundEuros = new Intl.NumberFormat('en', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
});

/** The `count` months up to and including `last`, oldest first, as "yyyy-MM". */
export function monthsEndingWith(last: string, count: number): string[] {
  const end = toDate(last);
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(end.getFullYear(), end.getMonth() - (count - 1 - index), 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
}

/**
 * The last 12 months at a glance: each Pot's balance over time (debts below zero), and each
 * month's Income, Expenses and Remainder. Months before the first Entry are left out.
 */
@Component({
  imports: [BaseChartDirective, CurrencyPipe, RouterLink],
  providers: [
    provideCharts({
      registerables: [
        BarController,
        BarElement,
        CategoryScale,
        Legend,
        LinearScale,
        LineController,
        LineElement,
        PointElement,
        Tooltip,
      ],
    }),
  ],
  selector: 'app-trends-page',
  styleUrl: './trends-page.scss',
  templateUrl: './trends-page.html',
})
export class TrendsPage {
  private readonly monthApi = inject(MonthApi);

  protected readonly current = currentMonth();

  /** Every month in range, fetched together. */
  protected readonly months = rxResource({
    stream: () =>
      forkJoin(
        monthsEndingWith(this.current, TREND_MONTHS).map((month) => this.monthApi.getMonth(month)),
      ),
  });

  /** The months in range from the first one with any Entries. */
  protected readonly shown = computed<MonthSummary[]>(() => {
    if (!this.months.hasValue()) {
      return [];
    }
    const months = this.months.value();
    const first = months.findIndex((month) => month.entries.length > 0);
    return first === -1 ? [] : months.slice(first);
  });

  protected readonly labels = computed(() =>
    this.shown().map((month) => monthLabel(month.month, 'short')),
  );

  /** One line per Pot seen in range, debts negative; a gap where the Pot wasn't in use. */
  protected readonly potLines = computed(() => {
    const months = this.shown();
    const pots = new Map<number, string>();
    for (const month of months) {
      for (const pot of month.pots) {
        pots.set(pot.id, pot.name);
      }
    }
    return [...pots].map(([id, name], index) => ({
      id,
      name,
      colour: COLOURS[index % COLOURS.length],
      values: months.map((month) => {
        const pot = month.pots.find((candidate) => candidate.id === id);
        return pot ? (isDebt(pot.kind) ? -pot.balance : pot.balance) : null;
      }),
    }));
  });

  protected readonly potData = computed<ChartConfiguration<'line'>['data']>(() => ({
    labels: this.labels(),
    datasets: this.potLines().map((line) => ({
      label: line.name,
      data: line.values,
      borderColor: line.colour,
      backgroundColor: line.colour,
      tension: 0.25,
      spanGaps: false,
    })),
  }));

  protected readonly totalsData = computed<ChartConfiguration<'bar'>['data']>(() => ({
    labels: this.labels(),
    datasets: [
      {
        label: 'Income',
        data: this.shown().map((month) => month.totalIncome),
        backgroundColor: '#1e5bb8',
      },
      {
        label: 'Expenses',
        data: this.shown().map((month) => month.totalExpenses),
        backgroundColor: '#c2640f',
      },
      {
        label: 'Remainder',
        data: this.shown().map((month) => month.remainder),
        backgroundColor: '#1a7f4b',
      },
    ],
  }));

  private readonly axis = {
    ticks: { callback: (value: string | number) => roundEuros.format(Number(value)) },
  };

  protected readonly lineOptions: ChartConfiguration<'line'>['options'] = {
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    scales: { y: this.axis },
    plugins: {
      tooltip: {
        callbacks: {
          label: (item) => ` ${item.dataset.label}: ${euros.format(item.parsed.y ?? 0)}`,
        },
      },
    },
  };

  protected readonly barOptions: ChartConfiguration<'bar'>['options'] = {
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    scales: { y: this.axis },
    plugins: {
      tooltip: {
        callbacks: {
          label: (item) => ` ${item.dataset.label}: ${euros.format(item.parsed.y ?? 0)}`,
        },
      },
    },
  };

  protected monthLabel(month: string): string {
    return monthLabel(month, 'short');
  }
}
