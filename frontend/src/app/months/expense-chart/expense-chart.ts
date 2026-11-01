import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import { CategoryTotal } from '../month.model';

/**
 * The month's Expenses by category as ranked horizontal bars, largest first, each labelled with
 * its amount and share of the total.
 */
@Component({
  imports: [CurrencyPipe],
  selector: 'app-expense-chart',
  styleUrl: './expense-chart.scss',
  templateUrl: './expense-chart.html',
})
export class ExpenseChart {
  /** Largest first, as the month summary returns them. */
  readonly categories = input.required<CategoryTotal[]>();

  protected readonly bars = computed(() => {
    const categories = this.categories();
    const total = categories.reduce((sum, category) => sum + category.amount, 0);
    const largest = categories[0]?.amount ?? 0;
    return categories.map((category) => ({
      label: category.category,
      amount: category.amount,
      percent: Math.round((category.amount / total) * 100),
      // Bars are scaled to the largest category, so it fills the track.
      width: (category.amount / largest) * 100,
    }));
  });
}
