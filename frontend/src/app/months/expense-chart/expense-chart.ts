import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { ArcElement, ChartConfiguration, PieController, Tooltip } from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';

import { CategoryTotal } from '../month.model';

/**
 * Categorical colours in fixed order, so a category's colour follows its rank this month.
 * Validated for colour-blind separation between neighbouring slices.
 */
const COLOURS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6'];
/** Everything past the coloured slices folds into one grey "Other" slice. */
const OTHER_COLOUR = '#9aa1ab';

const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });

interface Slice {
  label: string;
  amount: number;
  percent: number;
  colour: string;
}

/** Pie chart of the month's Expenses by category, with a list of the same figures beside it. */
@Component({
  imports: [BaseChartDirective, CurrencyPipe],
  // Only the pieces a pie needs, rather than all of Chart.js.
  providers: [provideCharts({ registerables: [PieController, ArcElement, Tooltip] })],
  selector: 'app-expense-chart',
  styleUrl: './expense-chart.scss',
  templateUrl: './expense-chart.html',
})
export class ExpenseChart {
  /** Largest first, as the month summary returns them. */
  readonly categories = input.required<CategoryTotal[]>();

  protected readonly slices = computed<Slice[]>(() => {
    const categories = this.categories();
    const total = categories.reduce((sum, category) => sum + category.amount, 0);
    const fits = categories.length <= COLOURS.length;
    const shown = fits ? categories : categories.slice(0, COLOURS.length - 1);
    const slices = shown.map((category, index) => ({
      label: category.category,
      amount: category.amount,
      colour: COLOURS[index],
    }));
    if (!fits) {
      slices.push({
        label: 'Other',
        amount: categories.slice(shown.length).reduce((sum, category) => sum + category.amount, 0),
        colour: OTHER_COLOUR,
      });
    }
    return slices.map((slice) => ({ ...slice, percent: Math.round((slice.amount / total) * 100) }));
  });

  protected readonly data = computed<ChartConfiguration<'pie'>['data']>(() => ({
    labels: this.slices().map((slice) => slice.label),
    datasets: [
      {
        data: this.slices().map((slice) => slice.amount),
        backgroundColor: this.slices().map((slice) => slice.colour),
        // A 2px gap of card colour between slices.
        borderColor: '#ffffff',
        borderWidth: 2,
        hoverOffset: 6,
      },
    ],
  }));

  protected readonly options: ChartConfiguration<'pie'>['options'] = {
    maintainAspectRatio: false,
    plugins: {
      tooltip: {
        callbacks: {
          label: (item) => {
            const slice = this.slices()[item.dataIndex];
            return ` ${euros.format(slice.amount)} (${slice.percent}%)`;
          },
        },
      },
    },
  };

  protected readonly summary = computed(() =>
    this.slices()
      .map((slice) => `${slice.label} ${slice.percent}%`)
      .join(', '),
  );
}
