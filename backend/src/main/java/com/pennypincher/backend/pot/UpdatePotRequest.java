package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.pennypincher.backend.entry.Account;

/**
 * Body of {@code PUT /api/pots/{id}}. The kind can't change: a Pot is permanently savings or a
 * debt. {@code archivedFrom} archives the Pot from that month; null keeps it in use.
 */
public record UpdatePotRequest(
		@NotBlank @Size(max = 50) String name,
		@NotNull Account account,
		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal openingBalance,
		@NotNull YearMonth openingMonth,
		YearMonth archivedFrom) {
}
