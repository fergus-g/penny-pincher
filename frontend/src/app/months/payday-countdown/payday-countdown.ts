import { Component, computed, signal } from '@angular/core';

import { daysToPayday, nextPayday } from '../month-label';

/**
 * Days until payday, the last Thursday of the month. Always counts from today, whichever month
 * is being viewed.
 */
@Component({
  selector: 'app-payday-countdown',
  styleUrl: './payday-countdown.scss',
  template: `
    <section class="card" aria-label="Payday">
      @if (days() === 0) {
        <p class="headline">Payday today</p>
      } @else {
        <p class="headline">
          <strong>{{ days() }}</strong> {{ days() === 1 ? 'day' : 'days' }} to payday
        </p>
      }
      <p class="date">{{ date() }}</p>
    </section>
  `,
})
export class PaydayCountdown {
  /** Today, read once when the card is created. */
  private readonly today = signal(new Date());

  protected readonly days = computed(() => daysToPayday(this.today()));
  protected readonly date = computed(() =>
    nextPayday(this.today()).toLocaleString('en-IE', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }),
  );
}
