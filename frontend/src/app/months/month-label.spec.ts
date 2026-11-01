import { ordinal, paydayWeeks } from './month-label';

describe('paydayWeeks', () => {
  // The same table as the backend's PaydayTest, so the form's sum matches the saved amount.
  const weeks: Record<string, number> = {
    '2026-01': 5,
    '2026-02': 4,
    '2026-03': 4,
    '2026-04': 5,
    '2026-05': 4,
    '2026-06': 4,
    '2026-07': 5,
    '2026-08': 4,
    '2026-09': 4,
    '2026-10': 5,
    '2026-11': 4,
    '2026-12': 5,
    '2027-01': 4,
    '2027-02': 4,
    '2027-03': 4,
    '2027-04': 5,
    '2027-05': 4,
    '2027-06': 4,
    '2027-07': 5,
    '2027-08': 4,
    '2027-09': 5,
    '2027-10': 4,
    '2027-11': 4,
    '2027-12': 5,
  };

  it('count payday to payday for every month of 2026 and 2027', () => {
    for (const [month, expected] of Object.entries(weeks)) {
      expect(paydayWeeks(month), month).toBe(expected);
    }
  });
});

describe('ordinal', () => {
  it('suffix each day of the month', () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 30, 31].map(ordinal)).toEqual([
      '1st',
      '2nd',
      '3rd',
      '4th',
      '11th',
      '12th',
      '13th',
      '21st',
      '22nd',
      '23rd',
      '30th',
      '31st',
    ]);
  });
});
