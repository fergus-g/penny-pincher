package com.pennypincher.backend.entry;

import java.math.BigDecimal;

/**
 * What must move from the home Account into {@code account} this month to cover it.
 * Negative when the Account takes in more than it pays out, so money moves back home.
 */
public record Transfer(Account account, BigDecimal amount) {
}
