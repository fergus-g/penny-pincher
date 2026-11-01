package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.pennypincher.backend.entry.Account;

public record PotResponse(
		Long id,
		String name,
		PotKind kind,
		Account account,
		BigDecimal openingBalance,
		YearMonth openingMonth,
		YearMonth archivedFrom) {

	static PotResponse from(Pot pot) {
		return new PotResponse(pot.getId(), pot.getName(), pot.getKind(), pot.getAccount(), pot.getOpeningBalance(),
				pot.getOpeningMonth(), pot.getArchivedFrom());
	}

}
