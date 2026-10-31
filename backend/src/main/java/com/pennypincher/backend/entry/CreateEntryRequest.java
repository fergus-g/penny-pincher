package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/entries}. {@code startMonth} is the month being viewed in the
 * app; a one-off entry ends in that same month, otherwise it recurs with no end.
 */
public record CreateEntryRequest(
		@NotNull EntryType type,
		@NotBlank @Size(max = 50) String category,
		@Size(max = 100) String name,
		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
		@NotNull YearMonth startMonth,
		boolean oneOff) {
}
