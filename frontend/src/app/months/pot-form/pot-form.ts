import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, model, OnInit, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { errorMessage } from '../error-message';
import { MonthApi } from '../month-api';
import { monthLabel } from '../month-label';
import {
  Account,
  ACCOUNTS,
  HOME_ACCOUNT,
  Pot,
  PotKind,
  POT_KINDS,
  UpdatePotRequest,
} from '../month.model';

/**
 * Add form when `pot` is null, edit form otherwise. Editing also offers Archive (from the month
 * being viewed) and Delete, which the backend refuses once the Pot has Contributions.
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-pot-form',
  styleUrl: '../entry-form/entry-form.scss',
  templateUrl: './pot-form.html',
})
export class PotForm implements OnInit {
  private readonly monthApi = inject(MonthApi);

  readonly pot = input<Pot | null>(null);
  /** The month being viewed: a new Pot opens here, and Archive hides it from here. */
  readonly month = input.required<string>();

  readonly saved = output<void>();
  readonly deleted = output<void>();
  readonly cancelled = output<void>();

  /** True while a request is in flight. A model so the page can block closing the dialog. */
  readonly saving = model(false);
  /** Delete takes two clicks: the first turns the button into "Confirm delete". */
  protected readonly confirmingDelete = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly title = computed(() => (this.pot() ? 'Edit pot' : 'Add pot'));
  protected readonly monthLabel = computed(() => monthLabel(this.month()));
  protected readonly kinds = POT_KINDS;
  protected readonly accounts = ACCOUNTS;

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(50)],
    }),
    kind: new FormControl<PotKind>('SAVINGS', { nonNullable: true }),
    account: new FormControl<Account>(HOME_ACCOUNT, { nonNullable: true }),
    openingBalance: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.pattern(/^\d+(\.\d{1,2})?$/),
    ]),
    openingMonth: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    const pot = this.pot();
    if (pot) {
      this.form.setValue({
        name: pot.name,
        kind: pot.kind,
        account: pot.account,
        openingBalance: pot.openingBalance,
        openingMonth: pot.openingMonth,
      });
      // A Pot is permanently savings or a debt.
      this.form.controls.kind.disable();
    } else {
      this.form.controls.openingMonth.setValue(this.month());
    }
  }

  protected submit(): void {
    this.save(this.pot()?.archivedFrom ?? null);
  }

  protected archive(): void {
    this.save(this.month());
  }

  private save(archivedFrom: string | null): void {
    this.confirmingDelete.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, kind, account, openingBalance, openingMonth } = this.form.getRawValue();
    const body: UpdatePotRequest = {
      name: name.trim(),
      account,
      openingBalance: openingBalance!,
      openingMonth,
      archivedFrom,
    };
    const pot = this.pot();
    const request: Observable<unknown> = pot
      ? this.monthApi.updatePot(pot.id, body)
      : this.monthApi.createPot({
          name: body.name,
          kind,
          account,
          openingBalance: body.openingBalance,
          openingMonth,
        });
    this.run(request, () => this.saved.emit(), "Couldn't save. Is the backend running?");
  }

  protected delete(): void {
    if (!this.confirmingDelete()) {
      this.confirmingDelete.set(true);
      return;
    }
    this.run(
      this.monthApi.deletePot(this.pot()!.id),
      () => this.deleted.emit(),
      "Couldn't delete. Is the backend running?",
    );
  }

  private run(request: Observable<unknown>, done: () => void, fallback: string): void {
    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        done();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.confirmingDelete.set(false);
        this.error.set(errorMessage(error, fallback));
      },
    });
  }
}
