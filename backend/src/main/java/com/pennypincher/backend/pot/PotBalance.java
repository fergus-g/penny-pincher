package com.pennypincher.backend.pot;

import java.math.BigDecimal;

import com.pennypincher.backend.entry.Account;

/**
 * A Pot's balance at the end of one month: positive, even for a debt. {@code checkedIn} is true
 * when that month has a Balance Check-in rather than a calculated balance.
 */
public record PotBalance(Long id, String name, PotKind kind, Account account, BigDecimal balance,
		boolean checkedIn) {
}
