package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.pennypincher.backend.entry.Account;

public record AllocationResponse(
		Long id,
		YearMonth effectiveFrom,
		BigDecimal savings,
		BigDecimal allowance,
		Account allowanceAccount,
		Long savingsPotId,
		Long debtPotId) {

	static AllocationResponse from(Allocation allocation) {
		return new AllocationResponse(allocation.getId(), allocation.getEffectiveFrom(), allocation.getSavings(),
				allocation.getAllowance(), allocation.getAllowanceAccount(), allocation.getSavingsPotId(),
				allocation.getDebtPotId());
	}

}
