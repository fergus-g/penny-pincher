package com.pennypincher.backend.pot;

public enum PotKind {

	SAVINGS,
	LOAN,
	OVERDRAFT,
	CREDIT_CARD;

	/** A Debt Pot holds money owed: Contributions pay it down instead of adding to it. */
	public boolean isDebt() {
		return this != SAVINGS;
	}

}
