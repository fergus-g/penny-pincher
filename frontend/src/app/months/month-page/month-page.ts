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

import { EntryForm } from '../entry-form/entry-form';
import { MonthApi } from '../month-api';
import { monthLabel, toDate } from '../month-label';
import { Entry, EntryType, MonthSummary } from '../month.model';

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

/** How many recent categories the add dialog offers as quick picks. */
const RECENT_CATEGORIES = 6;

@Component({
  imports: [CurrencyPipe, EntryForm],
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

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  /** What the dialog's form is for; null while the dialog is closed. */
  protected readonly editing = signal<{ type: EntryType; entry: Entry | null } | null>(null);
  /** True while the form is saving or deleting; the dialog can't be closed then. */
  protected readonly saving = signal(false);

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

  /** Scrolls the row into view if it's off-screen, then flashes it once it's visible. */
  private reveal(row: HTMLElement, id: number): void {
    const { top, bottom } = row.getBoundingClientRect();
    if (top < 0 || bottom > window.innerHeight) {
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

  /** Esc fires `cancel` on the dialog; block it while a save is in flight. */
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

  protected entryLabel(entry: Entry): string {
    return entry.name ?? entry.category;
  }
}
