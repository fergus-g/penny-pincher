package com.pennypincher.backend.entry;

import java.math.BigDecimal;

/**
 * What must be put into {@code account} this month: the total of the Expenses paid out of it.
 * {@code left} is how much of it is still to go out: the Expenses not yet paid. Null for an
 * Account whose Payments aren't tracked.
 */
public record Transfer(Account account, BigDecimal amount, BigDecimal left) {
}
