package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.pennypincher.backend.entry.Account;

/**
 * How one month's Remainder is split by the Allocation in force. A null Pot means that share
 * is unassigned and moves no balance.
 *
 * @param debtRepayment whatever is left after Savings and Allowance, never below zero
 * @param shortfall Savings plus Allowance exceed the Remainder; neither is reduced
 * @param debtOverflow the part of Debt Repayment the Debt Pot couldn't take because it was
 * cleared; it goes to the Savings Pot
 * @param debtPaidOffIn when the Debt Pot reached zero, if it's at zero this month
 */
public record AllocationSummary(
		YearMonth effectiveFrom,
		BigDecimal savings,
		BigDecimal allowance,
		Account allowanceAccount,
		BigDecimal debtRepayment,
		boolean shortfall,
		Long savingsPotId,
		String savingsPotName,
		Long debtPotId,
		String debtPotName,
		BigDecimal debtOverflow,
		YearMonth debtPaidOffIn) {
}
