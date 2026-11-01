package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.pennypincher.backend.allocation.AllocationSummary;
import com.pennypincher.backend.entry.Account;

/**
 * What one month's summary needs from Pots and the Allocation.
 *
 * @param pots each Pot in use that month, with its end-of-month balance
 * @param allocation the month's split of the Remainder; null with no Allocation in force
 * @param allocationMoves per Account, what the Allocation sends there this month
 */
public record PotMonth(List<PotBalance> pots, AllocationSummary allocation,
		Map<Account, BigDecimal> allocationMoves) {
}
