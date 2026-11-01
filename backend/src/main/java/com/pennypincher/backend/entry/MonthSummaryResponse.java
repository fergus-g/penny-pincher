package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.pennypincher.backend.allocation.AllocationSummary;
import com.pennypincher.backend.pot.PotBalance;

/**
 * Everything the month page shows. {@code allocation} is null for a month before any
 * Allocation starts.
 */
public record MonthSummaryResponse(
		YearMonth month,
		List<EntryResponse> entries,
		BigDecimal totalIncome,
		BigDecimal totalExpenses,
		BigDecimal remainder,
		List<Transfer> transfers,
		List<CategoryTotal> expensesByCategory,
		List<PotBalance> pots,
		AllocationSummary allocation) {
}
