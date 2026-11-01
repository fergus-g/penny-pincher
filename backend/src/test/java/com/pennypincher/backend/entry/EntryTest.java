package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where an Expense's Due Day falls in a month: past the end of a shorter month it falls on the
 * last day.
 */
class EntryTest {

	@Test
	void theThirtyFirstFallsOnTheThirtiethInNovember() {
		assertThat(dueOn(31).dueDayIn(YearMonth.of(2026, 11))).isEqualTo(30);
	}

	@Test
	void theThirtyFirstFallsOnTheTwentyEighthInFebruary() {
		assertThat(dueOn(31).dueDayIn(YearMonth.of(2026, 2))).isEqualTo(28);
	}

	@Test
	void theTwentyNinthStaysInALeapFebruary() {
		assertThat(dueOn(29).dueDayIn(YearMonth.of(2028, 2))).isEqualTo(29);
	}

	@Test
	void theTwentyNinthFallsOnTheTwentyEighthInAnOrdinaryFebruary() {
		assertThat(dueOn(29).dueDayIn(YearMonth.of(2027, 2))).isEqualTo(28);
	}

	@Test
	void aDayEveryMonthHasIsUnchanged() {
		assertThat(dueOn(15).dueDayIn(YearMonth.of(2026, 2))).isEqualTo(15);
		assertThat(dueOn(31).dueDayIn(YearMonth.of(2026, 12))).isEqualTo(31);
	}

	@Test
	void noDueDayStaysNone() {
		assertThat(dueOn(null).dueDayIn(YearMonth.of(2026, 11))).isNull();
	}

	private static Entry dueOn(Integer day) {
		Entry entry = new Entry(EntryType.EXPENSE, "Housing", "Rent", BigDecimal.TEN, Account.AIB,
				YearMonth.of(2026, 1), null);
		entry.setDueDay(day);
		return entry;
	}

}
