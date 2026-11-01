package com.pennypincher.backend.allocation;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code PUT /api/months/{month}/allocation}. Savings and Allowance are fixed euro
 * amounts; the Pots are optional targets.
 */
public record AllocationRequest(
		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal savings,
		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal allowance,
		Long savingsPotId,
		Long debtPotId) {
}
