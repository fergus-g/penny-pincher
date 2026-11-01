package com.pennypincher.backend.allocation;

import java.time.YearMonth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.pot.Pot;
import com.pennypincher.backend.pot.PotRepository;

@Service
public class AllocationService {

	private final AllocationRepository allocationRepository;

	private final PotRepository potRepository;

	public AllocationService(AllocationRepository allocationRepository, PotRepository potRepository) {
		this.allocationRepository = allocationRepository;
		this.potRepository = potRepository;
	}

	/**
	 * Sets the Allocation from {@code month} onwards. If the Allocation in force already starts in
	 * that month it's changed in place; otherwise a new one starts there, so earlier months keep
	 * their split and Pot balances.
	 */
	@Transactional
	public AllocationResponse save(YearMonth month, AllocationRequest request) {
		requirePot(request.savingsPotId(), false, "Savings");
		requirePot(request.debtPotId(), true, "Debt Repayment");
		Allocation allocation = this.allocationRepository
			.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(month)
			.filter((current) -> current.getEffectiveFrom().equals(month))
			.orElseGet(() -> new Allocation(month));
		allocation.setSavings(request.savings());
		allocation.setAllowance(request.allowance());
		allocation.setSavingsPotId(request.savingsPotId());
		allocation.setDebtPotId(request.debtPotId());
		return AllocationResponse.from(this.allocationRepository.save(allocation));
	}

	/** Savings can only target a Savings Pot, and Debt Repayment only a Debt Pot. */
	private void requirePot(Long potId, boolean debt, String share) {
		if (potId == null) {
			return;
		}
		Pot pot = this.potRepository.findById(potId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pot " + potId + " not found"));
		if (pot.getKind().isDebt() != debt) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					share + " must go to a " + (debt ? "Debt" : "Savings") + " Pot");
		}
	}

}
