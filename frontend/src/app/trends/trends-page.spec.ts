import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';

import { MonthApi } from '../months/month-api';
import { Entry, MonthSummary, PotBalance } from '../months/month.model';
import { monthsEndingWith, TrendsPage } from './trends-page';

function summary(
  month: string,
  income: number,
  expenses: number,
  pots: PotBalance[],
): MonthSummary {
  const entries = income + expenses > 0 ? [{ id: 1 } as Entry] : [];
  return {
    month,
    entries,
    totalIncome: income,
    totalExpenses: expenses,
    remainder: income - expenses,
    transfers: [],
    expensesByCategory: [],
    pots,
    allocation: null,
  };
}

const pot = (id: number, name: string, kind: PotBalance['kind'], balance: number): PotBalance => ({
  id,
  name,
  kind,
  account: 'AIB',
  balance,
  checkedIn: false,
  projection: null,
});

class FakeMonthApi {
  months: Record<string, MonthSummary> = {};
  requested: string[] = [];

  getMonth(month: string): Observable<MonthSummary> {
    this.requested.push(month);
    return of(this.months[month] ?? summary(month, 0, 0, []));
  }
}

describe('monthsEndingWith', () => {
  it('lists the months up to and including the last, oldest first, across a year', () => {
    expect(monthsEndingWith('2026-02', 4)).toEqual(['2025-11', '2025-12', '2026-01', '2026-02']);
  });
});

describe('TrendsPage', () => {
  let api: FakeMonthApi;

  beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 10, 15, 12, 0));
    api = new FakeMonthApi();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  async function render() {
    TestBed.configureTestingModule({
      imports: [TrendsPage],
      providers: [provideRouter([]), { provide: MonthApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(TrendsPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const rows = (table: string) =>
      Array.from(el.querySelectorAll(`table.${table} tbody tr`), (row) =>
        Array.from(row.children, (cell) => cell.textContent!.trim()),
      );
    return { el, rows };
  }

  it('fetch the 12 months up to and including this one, and no future months', async () => {
    await render();
    expect(api.requested).toEqual(monthsEndingWith('2026-11', 12));
    expect(api.requested[0]).toBe('2025-12');
  });

  it('skip the months before the first Entry, and match each month view’s totals', async () => {
    api.months['2026-09'] = summary('2026-09', 4000, 3000, []);
    api.months['2026-10'] = summary('2026-10', 0, 0, []);
    api.months['2026-11'] = summary('2026-11', 4000, 4200, []);
    const { rows } = await render();

    expect(rows('totals-table')).toEqual([
      ['Sep 2026', '€4,000.00', '€3,000.00', '€1,000.00'],
      ['Oct 2026', '€0.00', '€0.00', '€0.00'],
      ['Nov 2026', '€4,000.00', '€4,200.00', '-€200.00'],
    ]);
  });

  it('draw a line per Pot, debts below zero, with a gap where a Pot was not in use', async () => {
    api.months['2026-10'] = summary('2026-10', 4000, 3000, [pot(1, 'Car loan', 'LOAN', 5000)]);
    api.months['2026-11'] = summary('2026-11', 4000, 3000, [
      pot(1, 'Car loan', 'LOAN', 4750),
      pot(2, 'Holiday', 'SAVINGS', 300),
    ]);
    const { el, rows } = await render();

    expect(
      Array.from(el.querySelectorAll('table.pots-table thead th'), (th) => th.textContent!.trim()),
    ).toEqual(['Month', 'Car loan', 'Holiday']);
    expect(rows('pots-table')).toEqual([
      ['Oct 2026', '-€5,000.00', '–'],
      ['Nov 2026', '-€4,750.00', '€300.00'],
    ]);
  });

  it('say so when there is nothing yet', async () => {
    const { el } = await render();
    expect(el.textContent).toContain('Nothing to show yet');
    expect(el.querySelector('canvas')).toBeNull();
  });

  it('link back to this month', async () => {
    const { el } = await render();
    expect(el.querySelector('header a')!.getAttribute('href')).toBe('/months/2026-11');
  });
});
