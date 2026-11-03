import { Entry } from './month.model';
import { isTickable, unpaidExpenses } from './payments';

/** 15 November 2026, mid-afternoon local time. */
const NOW = new Date(2026, 10, 15, 15, 0);

function expense(id: number, dueDayInMonth: number | null, paid: boolean | null): Entry {
  return {
    id,
    type: 'EXPENSE',
    category: 'Bills',
    name: `Bill ${id}`,
    amount: 10,
    weeklyRate: null,
    weeks: null,
    account: paid === null ? 'REVOLUT' : 'AIB',
    startMonth: '2026-11',
    endMonth: null,
    potId: null,
    dueDay: dueDayInMonth,
    dueDayInMonth,
    paid,
    createdAt: '2026-11-01T00:00:00Z',
  };
}

describe('isTickable', () => {
  it('allows AIB Expenses in the current month only', () => {
    expect(isTickable(expense(1, 1, false), '2026-11', NOW)).toBe(true);
    expect(isTickable(expense(1, 1, true), '2026-11', NOW)).toBe(true);
    expect(isTickable(expense(1, 1, true), '2026-10', NOW)).toBe(false);
    expect(isTickable(expense(1, 1, false), '2026-12', NOW)).toBe(false);
    expect(isTickable(expense(1, 1, null), '2026-11', NOW)).toBe(false);
  });
});

describe('unpaidExpenses', () => {
  it('lists unticked AIB Expenses whose Due Day has arrived, today included, soonest first', () => {
    const entries = [
      expense(1, 15, false),
      expense(2, 3, false),
      expense(3, 16, false),
      expense(4, 1, true),
      expense(5, 2, null),
      expense(6, null, false),
    ];
    expect(unpaidExpenses(entries, '2026-11', NOW).map((entry) => entry.id)).toEqual([2, 1]);
  });

  it('is empty outside the current month', () => {
    expect(unpaidExpenses([expense(1, 1, false)], '2026-10', NOW)).toEqual([]);
    expect(unpaidExpenses([expense(1, 1, false)], '2026-12', NOW)).toEqual([]);
  });
});
