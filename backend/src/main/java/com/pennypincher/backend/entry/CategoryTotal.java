package com.pennypincher.backend.entry;

import java.math.BigDecimal;

/**
 * The total of one category's Expenses in a month: a slice of the expense breakdown.
 */
public record CategoryTotal(String category, BigDecimal amount) {
}
