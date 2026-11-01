package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.pot.Pot;
import com.pennypincher.backend.pot.PotKind;
import com.pennypincher.backend.pot.PotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AllocationService}, with the repositories mocked.
 */
class AllocationServiceTest {

	private static final YearMonth JAN = YearMonth.of(2026, 1);

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	private final AllocationRepository allocationRepository = mock(AllocationRepository.class);

	private final PotRepository potRepository = mock(PotRepository.class);

	private final AllocationService allocationService = new AllocationService(this.allocationRepository,
			this.potRepository);

	@Test
	void savingInTheMonthTheAllocationStartsChangesItInPlace() {
		Allocation january = new Allocation(JAN);
		given(this.allocationRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(JAN))
			.willReturn(Optional.of(january));
		given(this.allocationRepository.save(january)).willReturn(january);
		AllocationResponse saved = this.allocationService.save(JAN, request(null, null));
		assertThat(saved.effectiveFrom()).isEqualTo(JAN);
		assertThat(january.getSavings()).isEqualByComparingTo("400.00");
	}

	@Test
	void savingInALaterMonthStartsANewAllocationThere() {
		Allocation january = new Allocation(JAN);
		january.setSavings(new BigDecimal("100.00"));
		given(this.allocationRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(MAR))
			.willReturn(Optional.of(january));
		given(this.allocationRepository.save(any(Allocation.class)))
			.willAnswer((invocation) -> invocation.getArgument(0));
		AllocationResponse saved = this.allocationService.save(MAR, request(null, null));
		assertThat(saved.effectiveFrom()).isEqualTo(MAR);
		assertThat(january.getSavings()).isEqualByComparingTo("100.00");
	}

	@Test
	void savingsCannotTargetADebtPot() {
		given(this.potRepository.findById(5L)).willReturn(Optional.of(pot(PotKind.LOAN)));
		assertBadRequest(() -> this.allocationService.save(JAN, request(5L, null)));
	}

	@Test
	void debtRepaymentCannotTargetASavingsPot() {
		given(this.potRepository.findById(5L)).willReturn(Optional.of(pot(PotKind.SAVINGS)));
		assertBadRequest(() -> this.allocationService.save(JAN, request(null, 5L)));
	}

	private void assertBadRequest(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(this.allocationRepository, never()).save(any(Allocation.class));
	}

	private static AllocationRequest request(Long savingsPotId, Long debtPotId) {
		return new AllocationRequest(new BigDecimal("400.00"), new BigDecimal("200.00"), savingsPotId, debtPotId);
	}

	private static Pot pot(PotKind kind) {
		return new Pot("Pot", kind, Account.AIB, BigDecimal.TEN, JAN);
	}

}
