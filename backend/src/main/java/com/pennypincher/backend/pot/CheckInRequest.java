package com.pennypincher.backend.pot;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code PUT /api/pots/{id}/check-ins/{month}}: the Pot's actual end-of-month balance.
 */
public record CheckInRequest(@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal balance) {
}
