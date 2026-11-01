import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, model, OnInit, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../error-message';
import { MonthApi } from '../month-api';
import { monthLabel } from '../month-label';
import { isDebt, PotBalance } from '../month.model';

/**
 * Records a Balance Check-in: the Pot's actual balance at the end of the month being viewed.
 * Later months are calculated from it.
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-check-in-form',
  styleUrl: '../entry-form/entry-form.scss',
  templateUrl: './check-in-form.html',
})
export class CheckInForm implements OnInit {
  private readonly monthApi = inject(MonthApi);

  readonly pot = input.required<PotBalance>();
  readonly month = input.required<string>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  /** True while the request is in flight. A model so the page can block closing the dialog. */
  readonly saving = model(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitle = computed(
    () => `${isDebt(this.pot().kind) ? 'Owed' : 'Saved'} at the end of ${monthLabel(this.month())}`,
  );

  protected readonly balance = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(0),
    Validators.pattern(/^\d+(\.\d{1,2})?$/),
  ]);
  protected readonly form = new FormGroup({ balance: this.balance });

  ngOnInit(): void {
    this.balance.setValue(this.pot().balance);
  }

  protected submit(): void {
    if (this.balance.invalid) {
      this.balance.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.monthApi.checkIn(this.pot().id, this.month(), this.balance.value!).subscribe({
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
