import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, model, OnInit, output, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../error-message';
import { MonthApi } from '../month-api';
import { monthLabel, paydayWeeks } from '../month-label';
import {
  Account,
  ACCOUNTS,
  Entry,
  EntryType,
  DEFAULT_ACCOUNT,
  Pot,
  UpdateEntryRequest,
} from '../month.model';

const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });

/**
 * Add form when `entry` is null, edit form otherwise. Emits `saved` once the backend accepts it,
 * or `deleted` once an edited Entry is deleted.
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-entry-form',
  styleUrl: './entry-form.scss',
  templateUrl: './entry-form.html',
})
export class EntryForm implements OnInit {
  private readonly monthApi = inject(MonthApi);

  readonly type = input.required<EntryType>();
  /** The month being viewed; new entries start here. */
  readonly month = input.required<string>();
  readonly entry = input<Entry | null>(null);
  /** Recently used categories, offered as quick picks when adding. */
  readonly categories = input<string[]>([]);
  /** Every Pot; an Expense can pay into one that's in use this month. */
  readonly pots = input<Pot[]>([]);

  /** Every category used before on this type. */
  private readonly knownCategories = rxResource({
    params: () => this.type(),
    stream: ({ params }) => this.monthApi.getCategories(params),
  });
  /** Autocomplete while typing a category; none if they couldn't be loaded. */
  protected readonly suggestions = computed(() =>
    this.knownCategories.hasValue() ? this.knownCategories.value() : [],
  );

  /** The Entry as the backend saved it. */
  readonly saved = output<Entry>();
  readonly deleted = output<void>();
  readonly cancelled = output<void>();

  private readonly typeName = computed(() => (this.type() === 'INCOME' ? 'income' : 'expense'));
  protected readonly title = computed(() => `${this.entry() ? 'Edit' : 'Add'} ${this.typeName()}`);
  /** The month the Entry starts in, e.g. "November 2026". */
  protected readonly startLabel = computed(() =>
    monthLabel(this.entry()?.startMonth ?? this.month()),
  );
  protected readonly subtitle = computed(() =>
    this.entry()
      ? `Started ${this.startLabel()} · the start month can't be changed`
      : `Starts in ${this.startLabel()}`,
  );
  protected readonly submitLabel = computed(() =>
    this.entry() ? 'Save changes' : `Add ${this.typeName()}`,
  );
  /** Pots open this month, plus the one this Expense already pays into even if it's since archived. */
  protected readonly potOptions = computed(() => {
    const month = this.entry()?.startMonth ?? this.month();
    return this.pots().filter(
      (pot) =>
        pot.id === this.entry()?.potId ||
        (pot.openingMonth <= month && (pot.archivedFrom === null || pot.archivedFrom > month)),
    );
  });

  /** True while a save or delete is in flight. A model so the page can block closing the dialog. */
  readonly saving = model(false);
  /** Delete takes two clicks: the first turns the button into "Confirm delete". */
  protected readonly confirmingDelete = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    category: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(50)],
    }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    amount: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.01),
      // At most 2 decimal places, matching the backend's @Digits(fraction = 2).
      Validators.pattern(/^-?\d+(\.\d{1,2})?$/),
    ]),
    /** Whether `amount` is the monthly amount or an Expense's Weekly Rate. */
    pricing: new FormControl<'MONTHLY' | 'WEEKLY'>('MONTHLY', { nonNullable: true }),
    account: new FormControl<Account>(DEFAULT_ACCOUNT, { nonNullable: true }),
    oneOff: new FormControl(false, { nonNullable: true }),
    potId: new FormControl<number | null>(null),
    dueDay: new FormControl<number | null>(null, [
      Validators.min(1),
      Validators.max(31),
      Validators.pattern(/^[0-9]+$/),
    ]),
  });
  protected readonly accounts = ACCOUNTS;

  private readonly pricing = toSignal(this.form.controls.pricing.valueChanges, {
    initialValue: this.form.controls.pricing.value,
  });
  private readonly amount = toSignal(this.form.controls.amount.valueChanges, {
    initialValue: this.form.controls.amount.value,
  });
  protected readonly weekly = computed(
    () => this.type() === 'EXPENSE' && this.pricing() === 'WEEKLY',
  );
  /** The month's sum for a Weekly Rate, e.g. "€180.00/week × 5 = €900.00"; null otherwise. */
  protected readonly weeklySum = computed(() => {
    const rate = Number(this.amount());
    if (!this.weekly() || !(rate > 0)) {
      return null;
    }
    const weeks = paydayWeeks(this.month());
    return `${euros.format(rate)}/week × ${weeks} = ${euros.format(rate * weeks)} in ${monthLabel(this.month())}`;
  });

  ngOnInit(): void {
    const entry = this.entry();
    if (entry) {
      this.form.setValue({
        category: entry.category,
        name: entry.name ?? '',
        amount: entry.weeklyRate ?? entry.amount,
        pricing: entry.weeklyRate !== null ? 'WEEKLY' : 'MONTHLY',
        account: entry.account ?? DEFAULT_ACCOUNT,
        oneOff: entry.endMonth === entry.startMonth,
        potId: entry.potId,
        dueDay: entry.dueDay,
      });
    }
  }

  protected pickCategory(category: string): void {
    this.form.controls.category.setValue(category);
  }

  protected submit(): void {
    this.confirmingDelete.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { category, name, amount, pricing, account, oneOff, potId, dueDay } =
      this.form.getRawValue();
    const weekly = this.type() === 'EXPENSE' && pricing === 'WEEKLY';
    const body: UpdateEntryRequest = {
      category: category.trim(),
      name: name.trim() || null,
      amount: weekly ? null : amount!,
      weeklyRate: weekly ? amount! : null,
      // Income lands outside the app, so only an Expense has an Account.
      account: this.type() === 'EXPENSE' ? account : null,
      oneOff,
      potId: this.type() === 'EXPENSE' ? potId : null,
      // An empty number input reads as null; Income never has a Due Day.
      dueDay: this.type() === 'EXPENSE' && dueDay !== null ? Number(dueDay) : null,
    };
    const entry = this.entry();
    const request = entry
      ? this.monthApi.updateEntry(entry.id, body)
      : this.monthApi.createEntry({ ...body, type: this.type(), startMonth: this.month() });

    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.saved.emit(saved);
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(errorMessage(error, "Couldn't save. Is the backend running?"));
      },
    });
  }

  protected delete(): void {
    if (!this.confirmingDelete()) {
      this.confirmingDelete.set(true);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.monthApi.deleteEntry(this.entry()!.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.deleted.emit();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(errorMessage(error, "Couldn't delete. Is the backend running?"));
      },
    });
  }
}
