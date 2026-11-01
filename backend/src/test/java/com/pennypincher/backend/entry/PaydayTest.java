package com.pennypincher.backend.entry;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payday weeks for every month of 2026 and 2027, worked out by hand from the calendar: the weeks
 * from the previous month's last Thursday to this month's.
 */
class PaydayTest {

	@ParameterizedTest(name = "{0} has {1} payday weeks")
	@CsvSource({
			// 2026: last Thursdays Dec 25 (2025), Jan 29, Feb 26, Mar 26, Apr 30, May 28, Jun 25,
			// Jul 30, Aug 27, Sep 24, Oct 29, Nov 26, Dec 31.
			"2026-01, 5", "2026-02, 4", "2026-03, 4", "2026-04, 5", "2026-05, 4", "2026-06, 4",
			"2026-07, 5", "2026-08, 4", "2026-09, 4", "2026-10, 5", "2026-11, 4", "2026-12, 5",
			// 2027: Jan 28, Feb 25, Mar 25, Apr 29, May 27, Jun 24, Jul 29, Aug 26, Sep 30, Oct 28,
			// Nov 25, Dec 30.
			"2027-01, 4", "2027-02, 4", "2027-03, 4", "2027-04, 5", "2027-05, 4", "2027-06, 4",
			"2027-07, 5", "2027-08, 4", "2027-09, 5", "2027-10, 4", "2027-11, 4", "2027-12, 5" })
	void weeksRunFromPaydayToPayday(String month, int weeks) {
		assertThat(Payday.weeksIn(YearMonth.parse(month))).isEqualTo(weeks);
	}

	@Test
	void decemberTwentyTwentySixPaydayIsTheThirtyFirst() {
		assertThat(Payday.in(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(Payday.weeksIn(YearMonth.of(2027, 1))).isEqualTo(4);
	}

}
