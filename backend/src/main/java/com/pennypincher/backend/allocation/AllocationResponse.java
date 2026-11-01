package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

public record AllocationResponse(
		Long id,
		YearMonth effectiveFrom,
		BigDecimal savings,
		BigDecimal allowance,
		Long savingsPotId,
		Long debtPotId) {

	static AllocationResponse from(Allocation allocation) {
		return new AllocationResponse(allocation.getId(), allocation.getEffectiveFrom(), allocation.getSavings(),
				allocation.getAllowance(), allocation.getSavingsPotId(), allocation.getDebtPotId());
	}

}
