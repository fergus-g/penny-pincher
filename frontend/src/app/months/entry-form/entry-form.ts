import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, model, OnInit, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { MonthApi } from '../month-api';
import { Entry, EntryType, UpdateEntryRequest } from '../month.model';

/** Turns a backend ProblemDetail (with per-field `errors`) into one line of text. */
function errorMessage(error: HttpErrorResponse): string {
  const fieldErrors: Record<string, string> | undefined = error.error?.errors;
  if (fieldErrors) {
    return Object.entries(fieldErrors)
      .map(([field, message]) => `${field} ${message}`)
      .join(', ');
  }
  return error.error?.detail ?? "Couldn't save. Is the backend running?";
}

/** Add form when `entry` is null, edit form otherwise. Emits `saved` once the backend accepts it. */
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

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly title = computed(
    () => `${this.entry() ? 'Edit' : 'Add'} ${this.type() === 'INCOME' ? 'income' : 'expense'}`,
  );
  /** True while the request is in flight. A model so the page can block closing the dialog. */
  readonly saving = model(false);
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
    oneOff: new FormControl(false, { nonNullable: true }),
  });

  ngOnInit(): void {
    const entry = this.entry();
    if (entry) {
      this.form.setValue({
        category: entry.category,
        name: entry.name ?? '',
        amount: entry.amount,
        oneOff: entry.endMonth === entry.startMonth,
      });
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { category, name, amount, oneOff } = this.form.getRawValue();
    const body: UpdateEntryRequest = {
      type: this.type(),
      category: category.trim(),
      name: name.trim() || null,
      amount: amount!,
      oneOff,
    };
    const entry = this.entry();
    const request = entry
      ? this.monthApi.updateEntry(entry.id, body)
      : this.monthApi.createEntry({ ...body, startMonth: this.month() });

    this.saving.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.emit();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
