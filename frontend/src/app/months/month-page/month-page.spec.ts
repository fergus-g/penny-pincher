import { DeferBlockState, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';

import { MonthApi } from '../month-api';
import {
  CreateEntryRequest,
  CreatePotRequest,
  Entry,
  MonthSummary,
  Pot,
  PotBalance,
} from '../month.model';
import { MonthPage } from './month-page';

function entry(
  id: number,
  type: Entry['type'],
  name: string,
  amount: number,
  account: Entry['account'] = 'AIB',
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
    createdAt: '2026-11-01T00:00:00Z',
  };
}

function summary(month: string, entries: Entry[], pots: PotBalance[] = []): MonthSummary {
  const total = (type: Entry['type'], on = entries) =>
    on.filter((e) => e.type === type).reduce((sum, e) => sum + e.amount, 0);
  const revolut = entries.filter((e) => e.account === 'REVOLUT');
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
      { account: 'REVOLUT', amount: total('EXPENSE', revolut) - total('INCOME', revolut) },
    ],
    expensesByCategory: Array.from(byCategory, ([category, amount]) => ({ category, amount })).sort(
      (a, b) => b.amount - a.amount,
    ),
    pots,
  };
}

/** A month API backed by an in-memory list of months, so the page sees real responses. */
class FakeMonthApi {
  months: Record<string, Entry[]> = {};
  /** Every Pot, and each month's Pot balances as the backend would calculate them. */
  pots: Pot[] = [];
  potBalances: Record<string, PotBalance[]> = {};
  checkIns: { potId: number; month: string; balance: number }[] = [];
  nextId = 100;

  getMonth(month: string): Observable<MonthSummary> {
    return of(summary(month, this.months[month] ?? [], this.potBalances[month] ?? []));
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

  /** Refuses like the backend does for a Pot with Contributions. */
  deletePot(id: number): Observable<void> {
    const detail = `Pot ${id} has Contributions; archive it instead`;
    return throwError(() => new HttpErrorResponse({ status: 409, error: { detail } }));
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
  values: { category: string; name: string; amount: string; account?: string },
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

  it('ask for a Transfer to cover Revolut Expenses', async () => {
    api.months['2026-11'] = [
      entry(1, 'INCOME', 'Salary', 3200),
      entry(2, 'EXPENSE', 'Netflix', 120, 'REVOLUT'),
      entry(3, 'EXPENSE', 'Gym', 180, 'REVOLUT'),
    ];
    const { el } = await render(api);

    expect(transferLines(el)).toEqual(['Transfer €300.00 to Revolut']);
  });

  it('ask to move money back to AIB when Revolut Income exceeds its Expenses', async () => {
    api.months['2026-11'] = [
      entry(1, 'INCOME', 'Refund', 500, 'REVOLUT'),
      entry(2, 'EXPENSE', 'Netflix', 120, 'REVOLUT'),
    ];
    const { el } = await render(api);

    expect(transferLines(el)).toEqual(['Move €380.00 back to AIB']);
  });

  it('are hidden with no Revolut activity', async () => {
    api.months['2026-11'] = [entry(1, 'INCOME', 'Salary', 3200), entry(2, 'EXPENSE', 'Rent', 1250)];
    const { el } = await render(api);

    expect(transfers(el)).toBeNull();
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
    expect(transferLines(page.el)).toEqual(['Transfer €12.00 to Revolut']);
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

  it('explain why a Pot with Contributions cannot be deleted', async () => {
    const page = await render(api);

    page.el.querySelector<HTMLButtonElement>('section.pots .pot-main')!.click();
    await page.fixture.whenStable();
    const deleteButton = () => page.el.querySelector<HTMLButtonElement>('dialog button.delete')!;
    deleteButton().click();
    await page.fixture.whenStable();
    deleteButton().click();
    await page.fixture.whenStable();

    expect(page.el.querySelector('dialog [role="alert"]')!.textContent).toContain(
      'archive it instead',
    );
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
