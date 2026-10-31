package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthSummaryResponse(
		YearMonth month,
		List<EntryResponse> entries,
		BigDecimal totalIncome,
		BigDecimal totalExpenses,
		BigDecimal remainder) {
}
