package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

public record EntryResponse(
		Long id,
		EntryType type,
		String category,
		String name,
		BigDecimal amount,
		Account account,
		YearMonth startMonth,
		YearMonth endMonth,
		Instant createdAt) {

	static EntryResponse from(Entry entry) {
		return new EntryResponse(entry.getId(), entry.getType(), entry.getCategory(), entry.getName(),
				entry.getAmount(), entry.getAccount(), entry.getStartMonth(), entry.getEndMonth(),
				entry.getCreatedAt());
	}

}
