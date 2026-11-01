import { CurrencyPipe } from '@angular/common';
import {
  afterRenderEffect,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { AllocationForm } from '../allocation-form/allocation-form';
import { CheckInForm } from '../check-in-form/check-in-form';
import { EntryForm } from '../entry-form/entry-form';
import { ExpenseChart } from '../expense-chart/expense-chart';
import { MonthApi } from '../month-api';
import { monthLabel, ordinal, toDate } from '../month-label';
import {
  accountLabel,
  Entry,
  EntryType,
  DEFAULT_ACCOUNT,
  isDebt,
  MonthSummary,
  Pot,
  PotBalance,
  potKindLabel,
} from '../month.model';
import { PotForm } from '../pot-form/pot-form';

/** The current month as "yyyy-MM". */
export function currentMonth(): string {
  return formatMonth(new Date());
}

function formatMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function shiftMonth(month: string, delta: number): string {
  const date = toDate(month);
  date.setMonth(date.getMonth() + delta);
  return formatMonth(date);
}

/** Per-section wording and which month total it shows. */
const SECTION = {
  INCOME: { title: 'Income', noun: 'Income', empty: 'No income this month', total: 'totalIncome' },
  EXPENSE: {
    title: 'Expenses',
    noun: 'Expense',
    empty: 'No expenses this month',
    total: 'totalExpenses',
  },
} as const satisfies Record<
  EntryType,
  { title: string; noun: string; empty: string; total: keyof MonthSummary }
>;

const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });

/** How many Entry rows a section shows before its list scrolls; matches the CSS. */
const MAX_ROWS = 5;

/** How many recent categories the add dialog offers as quick picks. */
const RECENT_CATEGORIES = 6;

@Component({
  imports: [AllocationForm, CheckInForm, CurrencyPipe, EntryForm, ExpenseChart, PotForm],
  selector: 'app-month-page',
  styleUrl: './month-page.scss',
  templateUrl: './month-page.html',
})
export class MonthPage {
  private readonly monthApi = inject(MonthApi);
  private readonly router = inject(Router);

  /** Bound from the `:month` route parameter, e.g. "2026-10". */
  readonly month = input.required<string>();

  protected readonly label = computed(() => monthLabel(this.month()));

  /** Refetches whenever the month changes. */
  protected readonly summary = rxResource({
    params: () => this.month(),
    stream: ({ params }) => this.monthApi.getMonth(params),
  });

  private readonly entries = computed(() =>
    this.summary.hasValue() ? this.summary.value().entries : [],
  );
  /** Which sections are open. Page-level, so it survives month changes but not a reload. */
  protected readonly open = signal<Record<EntryType, boolean>>({ INCOME: false, EXPENSE: false });

  protected readonly sections = computed(() => {
    const month = this.summary.hasValue() ? this.summary.value() : null;
    return (['INCOME', 'EXPENSE'] as const).map((type) => {
      const entries = this.entries().filter((entry) => entry.type === type);
      if (type === 'EXPENSE') {
        // By the day they go out; those with no Due Day last, each group in the order added.
        entries.sort(
          (a, b) => (a.dueDayInMonth ?? Infinity) - (b.dueDayInMonth ?? Infinity) || a.id - b.id,
        );
      }
      return {
        type,
        ...SECTION[type],
        entries,
        count: `${entries.length} ${entries.length === 1 ? 'entry' : 'entries'}`,
        total: month?.[SECTION[type].total] ?? 0,
        open: this.open()[type],
      };
    });
  });

  /**
   * With an Allocation: where every euro of Income goes, as a stacked bar of Expenses, Savings,
   * Allowance and Debt Repayment. In a Shortfall the segments fill Income in that order and what
   * doesn't fit is a red overflow past it. Null without an Allocation or with no Income, when the
   * plain spent bar shows instead.
   */
  protected readonly incomeBar = computed(() => {
    const month = this.summary.hasValue() ? this.summary.value() : null;
    const split = month?.allocation;
    if (!month || !split || month.totalIncome <= 0) {
      return null;
    }
    const income = month.totalIncome;
    const parts = [
      { part: 'expenses', label: 'Expenses', amount: month.totalExpenses },
      { part: 'savings', label: 'Savings', amount: split.savings },
      { part: 'allowance', label: 'Allowance', amount: split.allowance },
      { part: 'debt', label: 'Debt Repayment', amount: split.debtRepayment },
    ];
    const over = Math.max(0, parts.reduce((sum, part) => sum + part.amount, 0) - income);
    // The bar spans Income plus any overflow, so the overflow shows past where Income ends.
    const scale = income + over;
    let room = income;
    const segments = parts.map((part) => {
      const inside = Math.min(part.amount, room);
      room -= inside;
      return { ...part, width: (inside / scale) * 100 };
    });
    const described = parts.map((part) => `${part.label} ${euros.format(part.amount)}`).join(', ');
    return {
      segments,
      over,
      overWidth: (over / scale) * 100,
      label:
        `Income ${euros.format(income)}: ${described}` +
        (over > 0 ? `; Shortfall: ${euros.format(over)} more than Income` : ''),
    };
  });

  /** Share of income spent, for the summary bar; null when there's no income to compare with. */
  protected readonly spent = computed(() => {
    if (!this.summary.hasValue() || this.summary.value().totalIncome <= 0) {
      return null;
    }
    const { totalIncome, totalExpenses } = this.summary.value();
    const percent = Math.round((totalExpenses / totalIncome) * 100);
    return {
      percent,
      bar: Math.min(percent, 100),
      rest: percent <= 100 ? `${100 - percent}% left` : `${percent - 100}% over`,
    };
  });

  /** What to move between Accounts this month, worded for display; empty when nothing moves. */
  protected readonly transfers = computed(() => {
    if (!this.summary.hasValue()) {
      return [];
    }
    return this.summary
      .value()
      .transfers.map((transfer) => ({ ...transfer, label: accountLabel(transfer.account) }));
  });

  /** Each type's categories this month, most recently added first: quick picks when adding. */
  protected readonly recentCategories = computed(() => {
    const recent = (type: EntryType) =>
      [
        ...new Set(
          this.entries()
            .filter((entry) => entry.type === type)
            .map((entry) => entry.category)
            .reverse(),
        ),
      ].slice(0, RECENT_CATEGORIES);
    return { INCOME: recent('INCOME'), EXPENSE: recent('EXPENSE') };
  });

  /** Every Pot, archived ones too: for editing a Pot and the Expense form's Pot select. */
  private readonly allPots = rxResource({ stream: () => this.monthApi.getPots() });
  protected readonly potList = computed(() =>
    this.allPots.hasValue() ? this.allPots.value() : [],
  );

  /** This month's Pots, debts shown negative. */
  protected readonly pots = computed(() =>
    (this.summary.hasValue() ? this.summary.value().pots : []).map((pot) => ({
      pot,
      shown: isDebt(pot.kind) ? -pot.balance : pot.balance,
      meta: `${potKindLabel(pot.kind)} · ${accountLabel(pot.account)}`,
    })),
  );

  /** This month's Allocation, worded for display; null before the first Allocation starts. */
  protected readonly allocation = computed(() => {
    const month = this.summary.hasValue() ? this.summary.value() : null;
    const split = month?.allocation;
    if (!month || !split) {
      return null;
    }
    return {
      ...split,
      /** How far Savings plus Allowance overshoot the Remainder. */
      shortfallBy: split.savings + split.allowance - month.remainder,
      paidOffLabel: split.debtPaidOffIn ? monthLabel(split.debtPaidOffIn) : null,
    };
  });

  /** The second dialog, for the Pot, Check-in and Allocation forms. */
  private readonly potDialog = viewChild.required<ElementRef<HTMLDialogElement>>('potDialog');
  /** What the second dialog is showing; null while it's closed. */
  protected readonly potEditing = signal<
    | { form: 'pot'; pot: Pot | null }
    | { form: 'check-in'; pot: PotBalance }
    | { form: 'allocation' }
    | null
  >(null);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  /** What the dialog's form is for; null while the dialog is closed. */
  protected readonly editing = signal<{ type: EntryType; entry: Entry | null } | null>(null);
  /** True while the form is saving or deleting; the dialog can't be closed then. */
  protected readonly saving = signal(false);

  protected readonly MAX_ROWS = MAX_ROWS;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** A just-added Entry waiting to be scrolled to and flashed once its row renders. */
  private readonly pendingReveal = signal<number | null>(null);
  /** Waits for a revealed row to be on screen; one at a time. */
  private revealObserver: IntersectionObserver | null = null;
  /** The Entry whose row is flashing right now. */
  protected readonly flashingId = signal<number | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revealObserver?.disconnect());

    // Runs after each render: once the new Entry's row exists, reveal it.
    afterRenderEffect(() => {
      const id = this.pendingReveal();
      if (id === null || !this.summary.hasValue()) {
        return;
      }
      const row = this.host.nativeElement.querySelector<HTMLElement>(`[data-entry-id="${id}"]`);
      if (row) {
        this.pendingReveal.set(null);
        this.reveal(row, id);
      }
    });
  }

  protected toggle(type: EntryType): void {
    this.setOpen(type, !this.open()[type]);
  }

  private setOpen(type: EntryType, open: boolean): void {
    this.open.update((sections) => ({ ...sections, [type]: open }));
    if (!open) {
      // A collapsed row never fires animationend, so drop any flash rather than replay it later.
      this.endFlash();
    }
  }

  protected openAdd(type: EntryType): void {
    console.log('[MonthPage] add clicked', type);
    this.editing.set({ type, entry: null });
    this.dialog().nativeElement.showModal();
  }

  protected openEdit(entry: Entry): void {
    console.log('[MonthPage] edit clicked', entry.id);
    this.editing.set({ type: entry.type, entry });
    this.dialog().nativeElement.showModal();
  }

  protected onSaved(saved: Entry): void {
    const added = this.editing()?.entry === null;
    console.log('[MonthPage] entry saved', saved.id, added ? '(added)' : '(edited)');
    this.dialog().nativeElement.close();
    if (added) {
      this.setOpen(saved.type, true);
      this.pendingReveal.set(saved.id);
    }
    this.summary.reload();
  }

  /**
   * Scrolls the row into view if it's off-screen, or out of sight inside its section's scrolling
   * list, then flashes it once it's visible.
   */
  private reveal(row: HTMLElement, id: number): void {
    const { top, bottom } = row.getBoundingClientRect();
    const list = row.closest<HTMLElement>('.rows.scroll')?.getBoundingClientRect();
    if (
      top < Math.max(0, list?.top ?? 0) ||
      bottom > Math.min(window.innerHeight, list?.bottom ?? Infinity)
    ) {
      row.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    this.revealObserver?.disconnect();
    this.revealObserver = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        this.revealObserver?.disconnect();
        this.revealObserver = null;
        this.flashingId.set(id);
      }
    });
    this.revealObserver.observe(row);
  }

  protected endFlash(): void {
    this.flashingId.set(null);
  }

  protected onDeleted(): void {
    console.log('[MonthPage] entry deleted', this.editing()?.entry?.id);
    this.dialog().nativeElement.close();
    this.summary.reload();
  }

  protected openAddPot(): void {
    console.log('[MonthPage] add pot clicked');
    this.potEditing.set({ form: 'pot', pot: null });
    this.potDialog().nativeElement.showModal();
  }

  protected openEditPot(id: number): void {
    const pot = this.potList().find((candidate) => candidate.id === id);
    console.log('[MonthPage] edit pot clicked', id);
    if (pot) {
      this.potEditing.set({ form: 'pot', pot });
      this.potDialog().nativeElement.showModal();
    }
  }

  protected openCheckIn(pot: PotBalance): void {
    console.log('[MonthPage] check in clicked', pot.id);
    this.potEditing.set({ form: 'check-in', pot });
    this.potDialog().nativeElement.showModal();
  }

  protected openAllocation(): void {
    console.log('[MonthPage] allocate clicked');
    this.potEditing.set({ form: 'allocation' });
    this.potDialog().nativeElement.showModal();
  }

  /**
   * A Pot, Check-in or Allocation was saved or deleted: balances, Transfers and the Pot list may
   * all have changed.
   */
  protected onPotChanged(): void {
    console.log('[MonthPage] pot changed');
    this.potDialog().nativeElement.close();
    this.summary.reload();
    this.allPots.reload();
  }

  /** Esc fires `cancel` on a dialog; block it while a save is in flight. */
  protected onDialogCancel(event: Event): void {
    if (this.saving()) {
      console.log('[MonthPage] dialog close blocked while saving');
      event.preventDefault();
    }
  }

  protected previous(): void {
    const target = shiftMonth(this.month(), -1);
    console.log('[MonthPage] previous clicked →', target);
    this.router.navigate(['/months', target]);
  }

  protected next(): void {
    const target = shiftMonth(this.month(), 1);
    console.log('[MonthPage] next clicked →', target);
    this.router.navigate(['/months', target]);
  }

  protected thisMonth(): void {
    const target = currentMonth();
    console.log('[MonthPage] this month clicked →', target);
    this.router.navigate(['/months', target]);
  }

  /** The row's badge text, and a tone that picks its colours. */
  protected kind(entry: Entry): { label: string; tone: 'recurring' | 'one-off' | 'until' } {
    if (entry.endMonth === null) {
      return { label: 'Recurring', tone: 'recurring' };
    }
    return entry.endMonth === entry.startMonth
      ? { label: 'One-off', tone: 'one-off' }
      : { label: `Until ${monthLabel(entry.endMonth, 'short')}`, tone: 'until' };
  }

  /**
   * Category, the Account unless it's the default one, the Due Day, a Weekly Rate's sum, then the
   * Pot a Contribution pays into: "Food · Revolut · Due 1st · €180.00/week × 5 → Car loan".
   */
  protected entryMeta(entry: Entry): string {
    const pot = this.potList().find((candidate) => candidate.id === entry.potId);
    return (
      [
        entry.category,
        entry.account && entry.account !== DEFAULT_ACCOUNT ? accountLabel(entry.account) : null,
        entry.dueDayInMonth ? `Due ${ordinal(entry.dueDayInMonth)}` : null,
        entry.weeklyRate !== null
          ? `${euros.format(entry.weeklyRate)}/week × ${entry.weeks}`
          : null,
      ]
        .filter((part) => part !== null)
        .join(' · ') + (pot ? ` → ${pot.name}` : '')
    );
  }

  protected entryLabel(entry: Entry): string {
    return entry.name ?? entry.category;
  }
}
