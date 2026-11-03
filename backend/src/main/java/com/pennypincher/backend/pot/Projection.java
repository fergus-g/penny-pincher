package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * When a Pot reaches its target at the monthly amount going in: a Savings Pot its target, a Debt
 * Pot zero. An estimate: interest is ignored.
 *
 * @param target what the Pot is heading for: a Savings Pot's target, zero for a debt
 * @param monthly what goes in (or pays it down) each month
 * @param monthlyEdited true when {@code monthly} was set by hand rather than worked out from
 * Contributions and the Allocation
 * @param reached true when the balance has already reached the target
 * @param reachedIn the month the target is reached; null when already reached or nothing goes in
 */
public record Projection(BigDecimal target, BigDecimal monthly, boolean monthlyEdited, boolean reached,
		YearMonth reachedIn) {

	/**
	 * Projects from the balance at the end of {@code month}; the first monthly amount goes in the
	 * month after. Null for a Savings Pot with no target.
	 */
	static Projection of(PotKind kind, BigDecimal balance, BigDecimal target, BigDecimal monthly,
			boolean monthlyEdited, YearMonth month) {
		if (!kind.isDebt() && target == null) {
			return null;
		}
		BigDecimal goal = kind.isDebt() ? BigDecimal.ZERO : target;
		BigDecimal remaining = kind.isDebt() ? balance : goal.subtract(balance);
		if (remaining.signum() <= 0) {
			return new Projection(goal, monthly, monthlyEdited, true, null);
		}
		if (monthly.signum() <= 0) {
			return new Projection(goal, monthly, monthlyEdited, false, null);
		}
		long months = remaining.divide(monthly, 0, RoundingMode.CEILING).longValueExact();
		return new Projection(goal, monthly, monthlyEdited, false, month.plusMonths(months));
	}

}
