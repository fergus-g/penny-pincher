import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';

import { MonthApi } from '../month-api';
import { CreateEntryRequest, Entry, MonthSummary } from '../month.model';
import { MonthPage } from './month-page';

function entry(id: number, type: Entry['type'], name: string, amount: number): Entry {
  return {
    id,
    type,
    category: type === 'INCOME' ? 'Salary' : 'Housing',
    name,
    amount,
    startMonth: '2026-11',
    endMonth: null,
    createdAt: '2026-11-01T00:00:00Z',
  };
}

function summary(month: string, entries: Entry[]): MonthSummary {
  const total = (type: Entry['type']) =>
    entries.filter((e) => e.type === type).reduce((sum, e) => sum + e.amount, 0);
  return {
    month,
    entries,
    totalIncome: total('INCOME'),
    totalExpenses: total('EXPENSE'),
    remainder: total('INCOME') - total('EXPENSE'),
  };
}

/** A month API backed by an in-memory list of months, so the page sees real responses. */
class FakeMonthApi {
  months: Record<string, Entry[]> = {};
  nextId = 100;

  getMonth(month: string): Observable<MonthSummary> {
    return of(summary(month, this.months[month] ?? []));
  }

  createEntry(request: CreateEntryRequest): Observable<Entry> {
    const created: Entry = {
      ...entry(this.nextId++, request.type, request.name ?? request.category, request.amount),
      category: request.category,
    };
    this.months[request.startMonth] = [...(this.months[request.startMonth] ?? []), created];
    return of(created);
  }
}

async function render(api: FakeMonthApi, month = '2026-11') {
  TestBed.configureTestingModule({
    imports: [MonthPage],
    providers: [provideRouter([]), { provide: MonthApi, useValue: api }],
  });
  const fixture = TestBed.createComponent(MonthPage);
  fixture.componentRef.setInput('month', month);
  await fixture.whenStable();
  const el: HTMLElement = fixture.nativeElement;
  const section = (title: string) =>
    Array.from(el.querySelectorAll('section.entries')).find((s) =>
      s.querySelector('h2')?.textContent?.includes(title),
    ) as HTMLElement;
  const toggle = (title: string) =>
    section(title).querySelector<HTMLButtonElement>('button[aria-expanded]');
  const rows = (title: string) =>
    Array.from(section(title).querySelectorAll<HTMLElement>('.entry'));
  return { fixture, el, section, toggle, rows };
}

/** Fills in and submits the add dialog that the section's + button opens. */
async function addEntry(
  page: Awaited<ReturnType<typeof render>>,
  title: string,
  values: { category: string; name: string; amount: string },
) {
  page
    .section(title)
    .querySelector<HTMLButtonElement>(`button[aria-label="Add ${title}"]`)!
    .click();
  await page.fixture.whenStable();
  const form = page.el.querySelector('dialog form') as HTMLFormElement;
  for (const [field, value] of Object.entries(values)) {
    const input = form.querySelector<HTMLInputElement>(`[formControlName="${field}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }
  form.dispatchEvent(new Event('submit'));
  await page.fixture.whenStable();
}

/** Reports every observed element as visible straight away; jsdom has no layout. */
class VisibleIntersectionObserver {
  constructor(private readonly callback: IntersectionObserverCallback) {}
  observe(target: Element) {
    this.callback([{ target, isIntersecting: true } as IntersectionObserverEntry], this as never);
  }
  disconnect() {}
}

describe('MonthPage sections', () => {
  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', VisibleIntersectionObserver);
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => vi.unstubAllGlobals());

  let api: FakeMonthApi;

  beforeEach(() => {
    api = new FakeMonthApi();
    api.months['2026-11'] = [
      entry(1, 'INCOME', 'Salary', 3200),
      entry(2, 'EXPENSE', 'Rent', 1250),
      entry(3, 'EXPENSE', 'Groceries', 420),
    ];
  });

  it('start collapsed, still showing each count and total', async () => {
    const { section, toggle, rows } = await render(api);

    expect(toggle('Income')!.getAttribute('aria-expanded')).toBe('false');
    expect(toggle('Expenses')!.getAttribute('aria-expanded')).toBe('false');
    expect(rows('Income')).toHaveLength(0);
    expect(rows('Expenses')).toHaveLength(0);
    expect(section('Income').textContent).toContain('1 entry');
    expect(section('Income').textContent).toContain('€3,200.00');
    expect(section('Expenses').textContent).toContain('2 entries');
    expect(section('Expenses').textContent).toContain('€1,670.00');
  });

  it('open and close independently', async () => {
    const { fixture, toggle, rows } = await render(api);

    toggle('Expenses')!.click();
    await fixture.whenStable();
    expect(toggle('Expenses')!.getAttribute('aria-expanded')).toBe('true');
    expect(rows('Expenses').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Rent'),
      expect.stringContaining('Groceries'),
    ]);
    expect(rows('Income')).toHaveLength(0);

    toggle('Expenses')!.click();
    await fixture.whenStable();
    expect(toggle('Expenses')!.getAttribute('aria-expanded')).toBe('false');
    expect(rows('Expenses')).toHaveLength(0);
  });

  it('say so when empty, with nothing to open but still a way to add', async () => {
    api.months['2026-11'] = [entry(2, 'EXPENSE', 'Rent', 1250)];
    const { section, toggle } = await render(api);

    expect(section('Income').textContent).toContain('No income this month');
    expect(toggle('Income')).toBeNull();
    expect(section('Income').querySelector('button[aria-label="Add Income"]')).not.toBeNull();
  });

  it('stay open when moving to another month', async () => {
    api.months['2026-12'] = [entry(4, 'EXPENSE', 'Christmas gifts', 300)];
    const { fixture, toggle, rows } = await render(api);

    toggle('Expenses')!.click();
    fixture.componentRef.setInput('month', '2026-12');
    await fixture.whenStable();

    expect(toggle('Expenses')!.getAttribute('aria-expanded')).toBe('true');
    expect(rows('Expenses').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Christmas gifts'),
    ]);
  });

  it('open the section after an add and flash the new Entry once', async () => {
    const page = await render(api);

    await addEntry(page, 'Expenses', {
      category: 'Transport',
      name: 'Car service',
      amount: '292.01',
    });

    expect(page.toggle('Expenses')!.getAttribute('aria-expanded')).toBe('true');
    const added = page.rows('Expenses').at(-1)!;
    expect(added.textContent).toContain('Car service');
    expect(added.classList).toContain('flash');
    expect(page.rows('Expenses').filter((row) => row.classList.contains('flash'))).toHaveLength(1);
  });

  it('scroll to a new Entry only when it is off-screen', async () => {
    const page = await render(api);

    await addEntry(page, 'Expenses', { category: 'Food', name: 'Takeaway', amount: '25' });
    expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();

    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      top: window.innerHeight + 200,
      bottom: window.innerHeight + 240,
    } as DOMRect);
    await addEntry(page, 'Expenses', { category: 'Food', name: 'Pizza', amount: '18' });
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledTimes(1);
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
  });
});
