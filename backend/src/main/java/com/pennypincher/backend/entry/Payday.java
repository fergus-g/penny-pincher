package com.pennypincher.backend.entry;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Payday is the last Thursday of the month, and a month's weeks run from the previous payday to
 * this one. A Weekly Rate Expense costs its rate times those weeks.
 */
public final class Payday {

	private Payday() {
	}

	/** The last Thursday of {@code month}. */
	public static LocalDate in(YearMonth month) {
		return month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
	}

	/** Weeks from the previous month's payday to this month's: always 4 or 5. */
	public static int weeksIn(YearMonth month) {
		return (int) ChronoUnit.WEEKS.between(in(month.minusMonths(1)), in(month));
	}

}
