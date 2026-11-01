import { DeferBlockState, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';

import { MonthApi } from '../month-api';
import {
  Account,
  AllocationRequest,
  AllocationSummary,
  CreateEntryRequest,
  CreatePotRequest,
  Entry,
  MonthSummary,
  Pot,
  PotBalance,
  UpdatePotRequest,
} from '../month.model';
import { MonthPage } from './month-page';

function entry(
  id: number,
  type: Entry['type'],
  name: string,
  amount: number,
  account: Entry['account'] = type === 'EXPENSE' ? 'AIB' : null,
): Entry {
  return {
    id,
    type,
    category: type === 'INCOME' ? 'Salary' : 'Housing',
    name,
    amount,
    account,
    startMonth: '2026-11',
    endMonth: null,
    potId: null,
    dueDay: null,
    dueDayInMonth: null,
    createdAt: '2026-11-01T00:00:00Z',
  };
}

function summary(
  month: string,
  entries: Entry[],
  pots: PotBalance[] = [],
  allocation: AllocationSummary | null = null,
): MonthSummary {
  const total = (type: Entry['type'], on = entries) =>
    on.filter((e) => e.type === type).reduce((sum, e) => sum + e.amount, 0);
  const paidFrom = (account: Account) =>
    total(
      'EXPENSE',
      entries.filter((e) => e.account === account),
    );
  const byCategory = new Map<string, number>();
  for (const e of entries.filter((e) => e.type === 'EXPENSE')) {
    byCategory.set(e.category, (byCategory.get(e.category) ?? 0) + e.amount);
  }
  return {
    month,
    entries,
    totalIncome: total('INCOME'),
    totalExpenses: total('EXPENSE'),
    remainder: total('INCOME') - total('EXPENSE'),
    transfers: [
      { account: 'AIB', amount: paidFrom('AIB') },
      { account: 'REVOLUT', amount: paidFrom('REVOLUT') },
    ],
    expensesByCategory: Array.from(byCategory, ([category, amount]) => ({ category, amount })).sort(
      (a, b) => b.amount - a.amount,
    ),
    pots,
    allocation,
  };
}

/** [year, month number] of a "yyyy-MM" month; day 0 of the next month is this month's last day. */
function startOf(month: string): [number, number] {
  const [year, monthNumber] = month.split('-').map(Number);
  return [year, monthNumber];
}

/** A month API backed by an in-memory list of months, so the page sees real responses. */
class FakeMonthApi {
  months: Record<string, Entry[]> = {};
  /** Every Pot, and each month's Pot balances as the backend would calculate them. */
  pots: Pot[] = [];
  potBalances: Record<string, PotBalance[]> = {};
  checkIns: { potId: number; month: string; balance: number }[] = [];
  removedCheckIns: { potId: number; month: string }[] = [];
  /** Each month's Allocation split, as the backend would calculate it. */
  allocations: Record<string, AllocationSummary> = {};
  savedAllocations: { month: string; request: AllocationRequest }[] = [];
  nextId = 100;

  getMonth(month: string): Observable<MonthSummary> {
    return of(
      summary(
        month,
        this.months[month] ?? [],
        this.potBalances[month] ?? [],
        this.allocations[month] ?? null,
      ),
    );
  }

  getPots(): Observable<Pot[]> {
    return of(this.pots);
  }

  createPot(request: CreatePotRequest): Observable<Pot> {
    const pot: Pot = { id: this.nextId++, ...request, archivedFrom: null };
    this.pots = [...this.pots, pot];
    const { id, name, kind, account, openingBalance } = pot;
    this.potBalances[request.openingMonth] = [
      ...(this.potBalances[request.openingMonth] ?? []),
      { id, name, kind, account, balance: openingBalance, checkedIn: false },
    ];
    return of(pot);
  }

  updatePot(id: number, request: UpdatePotRequest): Observable<Pot> {
    this.pots = this.pots.map((pot) => (pot.id === id ? { ...pot, ...request } : pot));
    return of(this.pots.find((pot) => pot.id === id)!);
  }

  deleteCheckIn(potId: number, month: string): Observable<void> {
    this.removedCheckIns.push({ potId, month });
    this.potBalances[month] = (this.potBalances[month] ?? []).map((pot) =>
      pot.id === potId ? { ...pot, checkedIn: false } : pot,
    );
    return of(undefined);
  }

  saveAllocation(month: string, request: AllocationRequest): Observable<unknown> {
    this.savedAllocations.push({ month, request });
    return of({ effectiveFrom: month, ...request });
  }

  checkIn(potId: number, month: string, balance: number): Observable<unknown> {
    this.checkIns.push({ potId, month, balance });
    this.potBalances[month] = (this.potBalances[month] ?? []).map((pot) =>
      pot.id === potId ? { ...pot, balance, checkedIn: true } : pot,
    );
    return of({ month, balance });
  }

  getCategories(type: Entry['type']): Observable<string[]> {
    const all = Object.values(this.months).flat();
    return of([...new Set(all.filter((e) => e.type === type).map((e) => e.category))].sort());
  }

  createEntry(request: CreateEntryRequest): Observable<Entry> {
    const created: Entry = {
      ...entry(
        this.nextId++,
        request.type,
        request.name ?? request.category,
        request.amount,
        request.account,
      ),
      category: request.category,
      potId: request.potId,
      dueDay: request.dueDay,
      // Clamped to the start month's length, as the backend does.
      dueDayInMonth:
        request.dueDay === null
          ? null
          : Math.min(request.dueDay, new Date(...startOf(request.startMonth), 0).getDate()),
    };
    this.months[request.startMonth] = [...(this.months[request.startMonth] ?? []), created];
    return of(created);
  }

  deleteEntry(id: number): Observable<void> {
    for (const month of Object.keys(this.months)) {
      this.months[month] = this.months[month].filter((e) => e.id !== id);
    }
    return of(undefined);
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
  values: { category: string; name: string; amount: string; account?: string; dueDay?: string },
) {
  page.section(title).querySelector<HTMLButtonElement>('.add-button')!.click();
  await page.fixture.whenStable();
  const form = page.el.querySelector('dialog form') as HTMLFormElement;
  for (const [field, value] of Object.entries(values)) {
    const input = form.querySelector<HTMLInputElement>(`[formControlName="${field}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event(input.tagName === 'SELECT' ? 'change' : 'input'));
  }
  form.dispatchEvent(new Event('submit'));
  await page.fixture.whenStable();
}

/**
 * Reports every observed element as visible straight away; jsdom has no layout. Used by the
 * new-Entry reveal and by the chart's `@defer (on viewport)`.
 */
class VisibleIntersectionObserver {
  constructor(private readonly callback: IntersectionObserverCallback) {}
  observe(target: Element) {
    this.callback([{ target, isIntersecting: true } as IntersectionObserverEntry], this as never);
  }
  unobserve() {}
  disconnect() {}
}

const originalScrollIntoView = Element.prototype.scrollIntoView;

// jsdom can't draw on a canvas, so the pie chart gets no context and draws nothing. Its
// figures are still checked through the list beside it.
beforeEach(() => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
  vi.stubGlobal('IntersectionObserver', VisibleIntersectionObserver);
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

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

  afterEach(() => {
    Element.prototype.scrollIntoView = originalScrollIntoView;
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

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
    expect(section('Income').querySelector('.add-button')).not.toBeNull();
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
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({
      behavior: 'smooth',
      block: 'nearest',
    });
  });

  it('do not replay the flash after collapsing mid-flash', async () => {
    const page = await render(api);
    await addEntry(page, 'Expenses', {
      category: 'Transport',
      name: 'Car service',
      amount: '292.01',
    });

    page.toggle('Expenses')!.click();
    await page.fixture.whenStable();
    page.toggle('Expenses')!.click();
    await page.fixture.whenStable();

    expect(page.rows('Expenses').filter((row) => row.classList.contains('flash'))).toHaveLength(0);
  });

  it('delete an Entry from its edit dialog only after confirming', async () => {
    const page = await render(api);
    page.toggle('Expenses')!.click();
    await page.fixture.whenStable();
    page.rows('Expenses')[0].click();
    await page.fixture.whenStable();
    const deleteButton = () => page.el.querySelector<HTMLButtonElement>('dialog button.delete')!;

    deleteButton().click();
    await page.fixture.whenStable();
    expect(deleteButton().textContent).toContain('Confirm delete');
    expect(api.months['2026-11'].map((e) => e.id)).toContain(2);

    deleteButton().click();
    await page.fixture.whenStable();
    expect(api.months['2026-11'].map((e) => e.id)).not.toContain(2);
    expect(page.rows('Expenses').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Groceries'),
    ]);
  });
});

describe('MonthPage due days', () => {
  let api: FakeMonthApi;

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    api = new FakeMonthApi();
  });

  const due = (id: number, name: string, day: number | null): Entry => ({
    ...entry(id, 'EXPENSE', name, 10),
    dueDay: day,
    dueDayInMonth: day,
  });

  it('sort Expenses by Due Day, with no Due Day last, and show each one', async () => {
    api.months['2026-11'] = [
      due(1, 'Gym', 15),
      due(2, 'Takeaway', null),
      due(3, 'Mortgage', 1),
      due(4, 'Car loan', 28),
      due(5, 'Gift', null),
    ];
    const page = await render(api);
    page.toggle('Expenses')!.click();
    await page.fixture.whenStable();

    const rows = page.rows('Expenses');
    expect(rows.map((row) => row.querySelector('.entry-name')!.textContent!.trim())).toEqual([
      'Edit Mortgage',
      'Edit Gym',
      'Edit Car loan',
      'Edit Takeaway',
      'Edit Gift',
    ]);
    expect(rows[0].querySelector('.entry-meta')!.textContent).toContain('Housing · Due 1st');
    expect(rows[3].querySelector('.entry-meta')!.textContent).not.toContain('Due');
  });

  it('save a Due Day on an Expense, falling on the last day of a shorter month', async () => {
    const page = await render(api);

    await addEntry(page, 'Expenses', {
      category: 'Housing',
      name: 'Rent',
      amount: '1000',
      dueDay: '31',
    });

    expect(api.months['2026-11'][0]).toMatchObject({ dueDay: 31, dueDayInMonth: 30 });
    expect(page.rows('Expenses')[0].textContent).toContain('Due 30th');
  });

  it('offer no Due Day for Income', async () => {
    const page = await render(api);

    page.section('Income').querySelector<HTMLButtonElement>('.add-button')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog #entry-due-day')).toBeNull();
  });
});

describe('MonthPage transfers', () => {
  let api: FakeMonthApi;

  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', VisibleIntersectionObserver);
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    api = new FakeMonthApi();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const transfers = (el: HTMLElement) => el.querySelector('section.transfers');
  const transferLines = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('section.transfers li'), (li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );

  it("ask to put each Account's Expenses into it", async () => {
    api.months['2026-11'] = [
      entry(1, 'INCOME', 'Salary', 3200),
      entry(2, 'EXPENSE', 'Rent', 1250),
      entry(3, 'EXPENSE', 'Netflix', 120, 'REVOLUT'),
      entry(4, 'EXPENSE', 'Gym', 180, 'REVOLUT'),
    ];
    const { el } = await render(api);

    expect(transferLines(el)).toEqual(['Put €1,250.00 into AIB', 'Put €300.00 into Revolut']);
  });

  it('always list both Accounts, even with nothing paid out of one', async () => {
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 3200), entry(2, 'EXPENSE', 'Rent', 1250)];
    const { el } = await render(api);

    expect(transfers(el)).not.toBeNull();
    expect(transferLines(el)).toEqual(['Put €1,250.00 into AIB', 'Put €0.00 into Revolut']);
  });

  it('add Income with no Account', async () => {
    const page = await render(api);

    page.section('Income').querySelector<HTMLButtonElement>('.add-button')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog #entry-account')).toBeNull();
    await addEntry(page, 'Income', { category: 'Salary', name: 'Pay', amount: '3000' });

    expect(api.months['2026-11'][0]).toMatchObject({ type: 'INCOME', account: null });
  });

  it('pick up a new Entry added on Revolut, which shows its Account', async () => {
    const page = await render(api);

    await addEntry(page, 'Expenses', {
      category: 'Subscriptions',
      name: 'Spotify',
      amount: '12',
      account: 'REVOLUT',
    });

    expect(api.months['2026-11'][0].account).toBe('REVOLUT');
    expect(page.rows('Expenses')[0].textContent).toContain('Subscriptions · Revolut');
    expect(transferLines(page.el)).toEqual(['Put €0.00 into AIB', 'Put €12.00 into Revolut']);
  });
});

describe('MonthPage expense chart', () => {
  let api: FakeMonthApi;

  /** Renders the page, then loads the chart's `@defer` block as if it had scrolled into view. */
  async function renderWithChart(api: FakeMonthApi) {
    const page = await render(api);
    for (const block of await page.fixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    return page;
  }

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    api = new FakeMonthApi();
  });

  /** Each legend row as "label amount percent". */
  const slices = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('app-expense-chart li'), (li) =>
      Array.from(li.querySelectorAll('span:not(.swatch)'), (span) => span.textContent!.trim()).join(
        ' ',
      ),
    );

  it('list each category with its amount and share, largest first', async () => {
    api.months['2026-11'] = [
      entry(1, 'INCOME', 'Salary', 3200),
      { ...entry(2, 'EXPENSE', 'Rent', 750), category: 'Housing' },
      { ...entry(3, 'EXPENSE', 'Big shop', 200), category: 'Groceries' },
      { ...entry(4, 'EXPENSE', 'Top-up', 50), category: 'Groceries' },
    ];
    const { el } = await renderWithChart(api);

    expect(slices(el)).toEqual(['Housing €750.00 75%', 'Groceries €250.00 25%']);
    expect(el.querySelector('app-expense-chart canvas')!.getAttribute('aria-label')).toBe(
      'Pie chart of expenses: Housing 75%, Groceries 25%',
    );
  });

  it('fold categories past the seventh into one Other slice, still summing to total Expenses', async () => {
    api.months['2026-11'] = Array.from({ length: 9 }, (_, i) => ({
      ...entry(i + 1, 'EXPENSE', `Thing ${i}`, 100 - i),
      category: `Category ${i}`,
    }));
    const { el } = await renderWithChart(api);

    const rows = slices(el);
    expect(rows).toHaveLength(7);
    expect(rows.at(-1)).toBe('Other €279.00 32%');
    const amounts = rows.map((row) => Number(row.match(/€([\d,.]+)/)![1].replace(',', '')));
    expect(amounts.reduce((a, b) => a + b, 0)).toBe(864);
  });

  it('say so when there are no Expenses', async () => {
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 3200)];
    const { el } = await renderWithChart(api);

    expect(el.querySelector('app-expense-chart')!.textContent).toContain('No expenses this month');
    expect(el.querySelector('app-expense-chart canvas')).toBeNull();
  });
});

describe('Entry form category autocomplete', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  it('suggests categories already used on that type of Entry, from any month', async () => {
    const api = new FakeMonthApi();
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 3200)];
    api.months['2026-10'] = [{ ...entry(2, 'EXPENSE', 'Bus', 40), category: 'Transport' }];
    const page = await render(api);

    page.section('Expenses').querySelector<HTMLButtonElement>('.add-button')!.click();
    await page.fixture.whenStable();

    const input = page.el.querySelector<HTMLInputElement>('dialog #entry-category')!;
    const options = Array.from(
      page.el.querySelectorAll<HTMLOptionElement>(`datalist#${input.getAttribute('list')} option`),
      (option) => option.value,
    );
    expect(options).toEqual(['Transport']);
  });
});

describe('MonthPage pots', () => {
  let api: FakeMonthApi;

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    api = new FakeMonthApi();
    api.pots = [
      {
        id: 1,
        name: 'Car loan',
        kind: 'LOAN',
        account: 'AIB',
        openingBalance: 5000,
        openingMonth: '2026-01',
        archivedFrom: null,
      },
      {
        id: 2,
        name: 'Rainy day',
        kind: 'SAVINGS',
        account: 'REVOLUT',
        openingBalance: 1200,
        openingMonth: '2026-01',
        archivedFrom: null,
      },
    ];
    api.potBalances['2026-11'] = [
      { id: 1, name: 'Car loan', kind: 'LOAN', account: 'AIB', balance: 4750, checkedIn: false },
      {
        id: 2,
        name: 'Rainy day',
        kind: 'SAVINGS',
        account: 'REVOLUT',
        balance: 1300,
        checkedIn: true,
      },
    ];
  });

  /** Each Pot row's visible text, whitespace collapsed. */
  const potRows = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLElement>('section.pots .pot'), (row) =>
      row.textContent!.replace(/\s+/g, ' ').trim(),
    );

  /** Sets form fields by id, firing the event Angular's forms listen for. */
  function fill(el: HTMLElement, values: Record<string, string>) {
    for (const [id, value] of Object.entries(values)) {
      const field = el.querySelector<HTMLInputElement | HTMLSelectElement>(`dialog #${id}`)!;
      field.value = value;
      field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input'));
    }
  }

  it('show each Pot with its balance, debts negative', async () => {
    const { el } = await render(api);

    const rows = potRows(el);
    expect(rows[0]).toContain('Car loan');
    expect(rows[0]).toContain('Loan · AIB');
    expect(rows[0]).toContain('-€4,750.00');
    expect(rows[1]).toContain('Savings · Revolut');
    expect(rows[1]).toContain('€1,300.00');
    expect(rows[1]).toContain('Checked in');
  });

  it('add a Pot that opens in the month being viewed', async () => {
    const page = await render(api);

    page.el.querySelector<HTMLButtonElement>('section.pots .add-button')!.click();
    await page.fixture.whenStable();
    fill(page.el, {
      'pot-name': 'Credit card',
      'pot-kind': 'CREDIT_CARD',
      'pot-opening-balance': '300',
    });
    page.el.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await page.fixture.whenStable();

    expect(api.pots.at(-1)).toMatchObject({
      name: 'Credit card',
      kind: 'CREDIT_CARD',
      openingBalance: 300,
      openingMonth: '2026-11',
    });
    expect(potRows(page.el).at(-1)).toContain('-€300.00');
  });

  it('check in a Pot, starting from its current balance', async () => {
    const page = await render(api);

    page.el.querySelector<HTMLButtonElement>('section.pots .check-in-button')!.click();
    await page.fixture.whenStable();
    const balance = page.el.querySelector<HTMLInputElement>('dialog #check-in-balance')!;
    expect(balance.value).toBe('4750');
    fill(page.el, { 'check-in-balance': '4000' });
    page.el.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await page.fixture.whenStable();

    expect(api.checkIns).toEqual([{ potId: 1, month: '2026-11', balance: 4000 }]);
    expect(potRows(page.el)[0]).toContain('-€4,000.00');
    expect(potRows(page.el)[0]).toContain('Checked in');
  });

  it('archive a Pot from the month being viewed, with no way to delete it', async () => {
    const page = await render(api);

    page.el.querySelector<HTMLButtonElement>('section.pots .pot-main')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog button.delete')).toBeNull();
    const archive = page.el.querySelector<HTMLButtonElement>('dialog .side-action')!;
    expect(archive.textContent).toContain('Archive from November 2026');
    archive.click();
    await page.fixture.whenStable();

    expect(api.pots[0].archivedFrom).toBe('2026-11');
  });

  it('remove a Check-in, but only from a month that has one', async () => {
    const page = await render(api);
    const checkInButtons = () =>
      page.el.querySelectorAll<HTMLButtonElement>('section.pots .check-in-button');

    checkInButtons()[0].click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog .side-action')).toBeNull();
    page.el.querySelectorAll<HTMLButtonElement>('dialog .secondary')[0].click();
    await page.fixture.whenStable();

    checkInButtons()[1].click();
    await page.fixture.whenStable();
    const remove = page.el.querySelector<HTMLButtonElement>('dialog .side-action')!;
    expect(remove.textContent).toContain('Remove check-in');
    remove.click();
    await page.fixture.whenStable();

    expect(api.removedCheckIns).toEqual([{ potId: 2, month: '2026-11' }]);
    expect(potRows(page.el)[1]).not.toContain('Checked in');
  });

  it('show the Pot a Contribution pays into, and the Account only when it is not AIB', async () => {
    api.months['2026-11'] = [
      { ...entry(1, 'EXPENSE', 'Car repayment', 250), category: 'Loans', potId: 1 },
      { ...entry(2, 'EXPENSE', 'Netflix', 18, 'REVOLUT'), category: 'Subscriptions' },
      { ...entry(3, 'EXPENSE', 'Mortgage', 1100), category: 'Housing' },
    ];
    const page = await render(api);
    page.toggle('Expenses')!.click();
    await page.fixture.whenStable();

    const meta = page
      .rows('Expenses')
      .map((row) => row.querySelector('.entry-meta')!.firstChild!.textContent!.trim());
    expect(meta).toEqual(['Loans → Car loan', 'Subscriptions · Revolut', 'Housing']);
  });

  it('let an Expense pay into a Pot, but not Income', async () => {
    const page = await render(api);

    page.section('Expenses').querySelector<HTMLButtonElement>('.add-button')!.click();
    await page.fixture.whenStable();
    const select = page.el.querySelector<HTMLSelectElement>('dialog #entry-pot')!;
    expect(Array.from(select.options, (option) => option.textContent!.trim())).toEqual([
      'None',
      'Car loan',
      'Rainy day',
    ]);
    select.value = select.options[1].value;
    select.dispatchEvent(new Event('change'));
    fill(page.el, { 'entry-category': 'Loans', 'entry-amount': '250' });
    page.el.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await page.fixture.whenStable();
    expect(api.months['2026-11'].at(-1)).toMatchObject({ category: 'Loans', potId: 1 });

    page.section('Income').querySelector<HTMLButtonElement>('.add-button')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog #entry-pot')).toBeNull();
  });
});

describe('MonthPage allocation', () => {
  let api: FakeMonthApi;

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    api = new FakeMonthApi();
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 3000), entry(2, 'EXPENSE', 'Rent', 2000)];
    api.pots = [
      {
        id: 1,
        name: 'Car loan',
        kind: 'LOAN',
        account: 'AIB',
        openingBalance: 5000,
        openingMonth: '2026-01',
        archivedFrom: null,
      },
      {
        id: 2,
        name: 'Rainy day',
        kind: 'SAVINGS',
        account: 'REVOLUT',
        openingBalance: 0,
        openingMonth: '2026-01',
        archivedFrom: null,
      },
    ];
  });

  /** The Allocation card's text, whitespace collapsed. */
  const card = (el: HTMLElement) =>
    el.querySelector('section.allocation')!.textContent!.replace(/\s+/g, ' ');
  /** Each share as "name amount target". */
  const shares = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('section.allocation dl > div'), (share) =>
      Array.from(share.querySelectorAll('dt, strong, .target'), (part) =>
        part.textContent!.trim(),
      ).join(' '),
    );

  function split(overrides: Partial<AllocationSummary> = {}): AllocationSummary {
    return {
      effectiveFrom: '2026-11',
      savings: 400,
      allowance: 200,
      debtRepayment: 400,
      shortfall: false,
      savingsPotId: 2,
      savingsPotName: 'Rainy day',
      debtPotId: 1,
      debtPotName: 'Car loan',
      debtOverflow: 0,
      debtPaidOffIn: null,
      ...overrides,
    };
  }

  it('prompt to set one up when there is none', async () => {
    const page = await render(api);

    expect(card(page.el)).toContain('Set up your Allocation');
    page.el.querySelector<HTMLButtonElement>('section.allocation .add-button')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog app-allocation-form')).not.toBeNull();
  });

  it('show Savings, Allowance and Debt Repayment with where each goes', async () => {
    api.allocations['2026-11'] = split();
    const { el } = await render(api);

    expect(shares(el)).toEqual([
      'Savings €400.00 Rainy day',
      'Allowance €200.00 Spending money',
      'Debt Repayment €400.00 Car loan',
    ]);
    expect(card(el)).not.toContain('Shortfall');
  });

  it('warn about a Shortfall without reducing Savings or Allowance', async () => {
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 2500), entry(2, 'EXPENSE', 'Rent', 2000)];
    api.allocations['2026-11'] = split({ debtRepayment: 0, shortfall: true });
    const { el } = await render(api);

    expect(card(el)).toContain('Shortfall: Savings and Allowance are €100.00 more');
    expect(card(el)).toContain("They're still transferred in full");
    expect(shares(el)).toEqual([
      'Savings €400.00 Rainy day',
      'Allowance €200.00 Spending money',
      'Debt Repayment €0.00 Car loan',
    ]);
  });

  it('say when the debt was paid off and prompt for a new one', async () => {
    api.allocations['2026-11'] = split({ debtOverflow: 100, debtPaidOffIn: '2026-11' });
    const page = await render(api);

    expect(card(page.el)).toContain('Car loan was paid off in November 2026.');
    expect(card(page.el)).toContain('Debt Repayment now goes to Rainy day.');
    page.el.querySelector<HTMLButtonElement>('section.allocation .paid-off button')!.click();
    await page.fixture.whenStable();
    expect(page.el.querySelector('dialog app-allocation-form')).not.toBeNull();
  });

  it('save from the month being viewed, offering only the right kind of Pot for each share', async () => {
    const page = await render(api);
    page.el.querySelector<HTMLButtonElement>('.allocate-button')!.click();
    await page.fixture.whenStable();

    const options = (id: string) =>
      Array.from(page.el.querySelector<HTMLSelectElement>(`dialog #${id}`)!.options, (option) =>
        option.textContent!.trim(),
      );
    expect(options('allocation-savings-pot')).toEqual(['Not assigned', 'Rainy day']);
    expect(options('allocation-debt-pot')).toEqual(['Not assigned', 'Car loan']);

    for (const [id, value] of Object.entries({
      'allocation-savings': '400',
      'allocation-allowance': '200',
    })) {
      const input = page.el.querySelector<HTMLInputElement>(`dialog #${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    const debtPot = page.el.querySelector<HTMLSelectElement>('dialog #allocation-debt-pot')!;
    debtPot.value = debtPot.options[1].value;
    debtPot.dispatchEvent(new Event('change'));
    page.el.querySelector('dialog form')!.dispatchEvent(new Event('submit'));
    await page.fixture.whenStable();

    expect(api.savedAllocations).toEqual([
      {
        month: '2026-11',
        request: {
          savings: 400,
          allowance: 200,
          savingsPotId: null,
          debtPotId: 1,
        },
      },
    ]);
  });
});
