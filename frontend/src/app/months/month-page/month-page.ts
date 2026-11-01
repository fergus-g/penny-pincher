import { CurrencyPipe } from '@angular/common';
import {
  afterRenderEffect,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { EntryForm } from '../entry-form/entry-form';
import { MonthApi } from '../month-api';
import { Entry, EntryType } from '../month.model';

/** The current month as "yyyy-MM". */
export function currentMonth(): string {
  return formatMonth(new Date());
}

function formatMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function toDate(month: string): Date {
  const [year, monthNumber] = month.split('-').map(Number);
  return new Date(year, monthNumber - 1, 1);
}

function shiftMonth(month: string, delta: number): string {
  const date = toDate(month);
  date.setMonth(date.getMonth() + delta);
  return formatMonth(date);
}

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

  protected readonly label = computed(() =>
    toDate(this.month()).toLocaleString('en', { month: 'long', year: 'numeric' }),
  );

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

  protected readonly sections = computed(() =>
    (['INCOME', 'EXPENSE'] as const).map((type) => {
      const entries = this.entries().filter((entry) => entry.type === type);
      const month = this.summary.hasValue() ? this.summary.value() : null;
      return {
        type,
        title: type === 'INCOME' ? 'Income' : 'Expenses',
        entries,
        empty: type === 'INCOME' ? 'No income this month' : 'No expenses this month',
        count: `${entries.length} ${entries.length === 1 ? 'entry' : 'entries'}`,
        total: (type === 'INCOME' ? month?.totalIncome : month?.totalExpenses) ?? 0,
        open: this.open()[type],
      };
    }),
  );

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  /** What the dialog's form is for; null while the dialog is closed. */
  protected readonly editing = signal<{ type: EntryType; entry: Entry | null } | null>(null);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** A just-added Entry waiting to be scrolled to and flashed once its row renders. */
  private readonly pendingReveal = signal<number | null>(null);
  /** The Entry whose row is flashing right now. */
  protected readonly flashingId = signal<number | null>(null);

  constructor() {
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

  /** True while the form is saving; the dialog can't be closed then. */
  protected readonly saving = signal(false);
  /** Resets to null whenever the month changes, however the user navigated. */
  protected readonly deleteError = linkedSignal<string, string | null>({
    source: this.month,
    computation: () => null,
  });

  protected toggle(type: EntryType): void {
    this.open.update((open) => ({ ...open, [type]: !open[type] }));
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
      this.open.update((open) => ({ ...open, [saved.type]: true }));
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
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        observer.disconnect();
        this.flashingId.set(id);
      }
    });
    observer.observe(row);
  }

  protected endFlash(): void {
    this.flashingId.set(null);
  }

  protected remove(entry: Entry): void {
    if (!confirm(`Delete "${this.entryLabel(entry)}"?`)) {
      return;
    }
    console.log('[MonthPage] delete confirmed', entry.id);
    this.deleteError.set(null);
    this.monthApi.deleteEntry(entry.id).subscribe({
      next: () => this.summary.reload(),
      error: () => this.deleteError.set(`Couldn't delete "${this.entryLabel(entry)}".`),
    });
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

  protected kind(entry: Entry): string {
    if (entry.endMonth === null) {
      return 'Recurring';
    }
    return entry.endMonth === entry.startMonth ? 'One-off' : `Until ${entry.endMonth}`;
  }

  protected entryLabel(entry: Entry): string {
    return entry.name ?? entry.category;
  }
}
