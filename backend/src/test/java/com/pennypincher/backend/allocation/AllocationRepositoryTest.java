package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.pot.Pot;
import com.pennypincher.backend.pot.PotKind;
import com.pennypincher.backend.pot.PotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the V4 table and the Allocation queries against the real MySQL database. Each test rolls
 * back, and uses months far in the future so it can't meet a real Allocation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AllocationRepositoryTest {

	private static final YearMonth JAN = YearMonth.of(2099, 1);

	private static final YearMonth MAR = YearMonth.of(2099, 3);

	@Autowired
	private AllocationRepository allocationRepository;

	@Autowired
	private PotRepository potRepository;

	@Test
	void theAllocationInForceIsTheLatestToStartOnOrBeforeTheMonth() {
		save(JAN, null);
		save(MAR, null);
		assertThat(inForce(YearMonth.of(2099, 2))).isEqualTo(JAN);
		assertThat(inForce(MAR)).isEqualTo(MAR);
		assertThat(inForce(YearMonth.of(2099, 12))).isEqualTo(MAR);
	}

	@Test
	void onlyOneAllocationCanStartInAMonth() {
		save(JAN, null);
		assertThatThrownBy(() -> save(JAN, null)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aTargetedPotIsKnownToBeTargeted() {
		Long potId = this.potRepository
			.saveAndFlush(new Pot("Test pot", PotKind.SAVINGS, Account.AIB, BigDecimal.TEN, JAN))
			.getId();
		assertThat(this.allocationRepository.existsBySavingsPotIdOrDebtPotId(potId, potId)).isFalse();
		save(JAN, potId);
		assertThat(this.allocationRepository.existsBySavingsPotIdOrDebtPotId(potId, potId)).isTrue();
	}

	private void save(YearMonth from, Long savingsPotId) {
		Allocation allocation = new Allocation(from);
		allocation.setSavings(BigDecimal.ONE);
		allocation.setAllowance(BigDecimal.ONE);
		allocation.setAllowanceAccount(Account.AIB);
		allocation.setSavingsPotId(savingsPotId);
		this.allocationRepository.saveAndFlush(allocation);
	}

	private YearMonth inForce(YearMonth month) {
		return this.allocationRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(month)
			.orElseThrow()
			.getEffectiveFrom();
	}

}
