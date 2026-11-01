package com.pennypincher.backend.entry;

import java.math.BigDecimal;

/**
 * What must be put into {@code account} this month: the total of the Expenses paid out of it.
 */
public record Transfer(Account account, BigDecimal amount) {
}
