package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.pennypincher.backend.pot.PotBalance;

public record MonthSummaryResponse(
		YearMonth month,
		List<EntryResponse> entries,
		BigDecimal totalIncome,
		BigDecimal totalExpenses,
		BigDecimal remainder,
		List<Transfer> transfers,
		List<CategoryTotal> expensesByCategory,
		List<PotBalance> pots) {
}
