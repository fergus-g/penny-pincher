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
 * Body of {@code POST /api/pots}. {@code openingBalance} is the amount saved or owed at the end
 * of {@code openingMonth}, always positive.
 */
public record CreatePotRequest(
		@NotBlank @Size(max = 50) String name,
		@NotNull PotKind kind,
		@NotNull Account account,
		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal openingBalance,
		@NotNull YearMonth openingMonth) {
}
