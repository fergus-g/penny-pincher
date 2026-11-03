package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * An Entry as the API returns it. {@code dueDayInMonth} is the Due Day as it falls in the month
 * being returned (the Entry's start month after a create or update): never past the month's end.
 * {@code amount} is what the Entry comes to in that month; for a Weekly Rate it is
 * {@code weeklyRate} times {@code weeks}, otherwise both of those are null. {@code paid} is
 * whether an AIB Expense has gone out that month; null for an Entry that can't be ticked.
 */
public record EntryResponse(
		Long id,
		EntryType type,
		String category,
		String name,
		BigDecimal amount,
		BigDecimal weeklyRate,
		Integer weeks,
		Account account,
		YearMonth startMonth,
		YearMonth endMonth,
		Long potId,
		Integer dueDay,
		Integer dueDayInMonth,
		Boolean paid,
		Instant createdAt) {

	/** The Entry as it stands in {@code month}, which decides its amount and where its Due Day falls. */
	static EntryResponse from(Entry entry, YearMonth month, Boolean paid) {
		return new EntryResponse(entry.getId(), entry.getType(), entry.getCategory(), entry.getName(),
				entry.amountIn(month), entry.getWeeklyRate(),
				(entry.getWeeklyRate() != null) ? Payday.weeksIn(month) : null, entry.getAccount(), entry.getStartMonth(), entry.getEndMonth(), entry.getPotId(),
				entry.getDueDay(), entry.dueDayIn(month), paid, entry.getCreatedAt());
	}

}
