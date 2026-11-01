import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, model, OnInit, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../error-message';
import { MonthApi } from '../month-api';
import { monthLabel } from '../month-label';
import { Account, ACCOUNTS, AllocationSummary, HOME_ACCOUNT, isDebt, Pot } from '../month.model';

/** At least 0, at most 2 decimal places, matching the backend's @Digits(fraction = 2). */
const MONEY = [Validators.required, Validators.min(0), Validators.pattern(/^\d+(\.\d{1,2})?$/)];

/**
 * Sets the Allocation from the month being viewed onwards. Saving in the month the current
 * Allocation started changes it; saving in a later month starts a new one, so earlier months
 * keep their split.
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-allocation-form',
  styleUrl: '../entry-form/entry-form.scss',
  templateUrl: './allocation-form.html',
})
export class AllocationForm implements OnInit {
  private readonly monthApi = inject(MonthApi);

  readonly month = input.required<string>();
  /** The Allocation in force this month, if any, to start from. */
  readonly allocation = input<AllocationSummary | null>(null);
  /** Every Pot; the targets offered are the ones open this month. */
  readonly pots = input<Pot[]>([]);

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  /** True while the request is in flight. A model so the page can block closing the dialog. */
  readonly saving = model(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitle = computed(() => {
    const label = monthLabel(this.month());
    const current = this.allocation();
    if (!current) {
      return `From ${label} onwards`;
    }
    return current.effectiveFrom === this.month()
      ? `Changes the Allocation that started in ${label}`
      : `From ${label} onwards; months before keep the Allocation from ${monthLabel(current.effectiveFrom)}`;
  });

  private readonly openPots = computed(() =>
    this.pots().filter(
      (pot) =>
        pot.openingMonth <= this.month() &&
        (pot.archivedFrom === null || pot.archivedFrom > this.month()),
    ),
  );
  protected readonly savingsPots = computed(() =>
    this.openPots().filter((pot) => !isDebt(pot.kind)),
  );
  protected readonly debtPots = computed(() => this.openPots().filter((pot) => isDebt(pot.kind)));
  protected readonly accounts = ACCOUNTS;

  protected readonly form = new FormGroup({
    savings: new FormControl<number | null>(null, MONEY),
    allowance: new FormControl<number | null>(null, MONEY),
    allowanceAccount: new FormControl<Account>(HOME_ACCOUNT, { nonNullable: true }),
    savingsPotId: new FormControl<number | null>(null),
    debtPotId: new FormControl<number | null>(null),
  });

  ngOnInit(): void {
    const current = this.allocation();
    if (current) {
      this.form.setValue({
        savings: current.savings,
        allowance: current.allowance,
        allowanceAccount: current.allowanceAccount,
        savingsPotId: current.savingsPotId,
        debtPotId: current.debtPotId,
      });
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { savings, allowance, allowanceAccount, savingsPotId, debtPotId } =
      this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.monthApi
      .saveAllocation(this.month(), {
        savings: savings!,
        allowance: allowance!,
        allowanceAccount,
        savingsPotId,
        debtPotId,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.saved.emit();
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.error.set(errorMessage(error, "Couldn't save. Is the backend running?"));
        },
      });
  }
}
