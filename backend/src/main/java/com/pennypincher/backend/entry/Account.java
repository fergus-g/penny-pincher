package com.pennypincher.backend.entry;

/**
 * The bank account an Entry is paid out of or lands in.
 */
public enum Account {

	AIB,
	REVOLUT;

	/** Income lands here; every other Account is topped up from it by a Transfer. */
	public static final Account HOME = AIB;

}
