package com.pennypincher.backend.entry;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/entries/{id}}. The type and start month can't change: an entry is
 * permanently Income or an Expense, and stays in the month it was created in. {@code oneOff}
 * switches it between one-off and recurring. {@code potId} links an Expense to a Pot, or
 * unlinks it when null.
 */
public record UpdateEntryRequest(
		@NotBlank @Size(max = 50) String category,
		@Size(max = 100) String name,
		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
		Account account,
		boolean oneOff,
		Long potId) {
}
