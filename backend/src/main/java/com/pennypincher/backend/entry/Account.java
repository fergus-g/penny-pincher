package com.pennypincher.backend.entry;

/**
 * A joint bank account that Expenses are paid out of, and that needs money put into it each
 * month (ADR 0002). Income lands in sole accounts the app doesn't track, so it has none.
 */
public enum Account {

	AIB,
	REVOLUT;

	/** Whether this Account's Expenses can be ticked as paid (ADR 0003): only AIB. */
	public boolean tracksPayments() {
		return this == AIB;
	}

}
