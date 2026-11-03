import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { daysToPayday, nextPayday } from '../month-label';
import { PaydayCountdown } from './payday-countdown';

// November 2026's payday is Thursday the 26th; December's is the 31st.
describe('daysToPayday', () => {
  it('counts down to this month’s last Thursday', () => {
    expect(daysToPayday(new Date(2026, 10, 3, 9, 0))).toBe(23);
    expect(daysToPayday(new Date(2026, 10, 25, 23, 59))).toBe(1);
  });

  it('is 0 on payday itself, whatever the time', () => {
    expect(daysToPayday(new Date(2026, 10, 26, 0, 0))).toBe(0);
    expect(daysToPayday(new Date(2026, 10, 26, 23, 30))).toBe(0);
  });

  it('counts to next month’s payday the day after', () => {
    expect(nextPayday(new Date(2026, 10, 27))).toEqual(new Date(2026, 11, 31));
    expect(daysToPayday(new Date(2026, 10, 27))).toBe(34);
  });

  it('crosses into a new year', () => {
    // 2027's first payday: Thursday 28 January.
    expect(nextPayday(new Date(2027, 0, 1))).toEqual(new Date(2027, 0, 28));
  });

  it('is unaffected by the October clock change', () => {
    // Clocks go back on 25 October 2026; payday is Thursday the 29th.
    expect(daysToPayday(new Date(2026, 9, 24, 12, 0))).toBe(5);
  });
});

describe('PaydayCountdown', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  async function render(now: Date): Promise<string> {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(now);
    const fixture = TestBed.createComponent(PaydayCountdown);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ').trim();
  }

  it('shows the days left and the date', async () => {
    expect(await render(new Date(2026, 10, 3, 9, 0))).toBe(
      '23 days to payday Thursday, 26 November',
    );
  });

  it('says one day, not one days', async () => {
    expect(await render(new Date(2026, 10, 25))).toContain('1 day to payday');
  });

  it('says so on payday', async () => {
    expect(await render(new Date(2026, 10, 26, 8, 0))).toContain('Payday today');
  });
});
