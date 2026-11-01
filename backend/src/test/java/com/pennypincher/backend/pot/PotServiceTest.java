package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.allocation.AllocationRepository;
import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.entry.EntryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PotService}, with the repositories mocked. The balance rules themselves
 * are covered in {@link MonthLedgerTest}.
 */
class PotServiceTest {

	private static final YearMonth JAN = YearMonth.of(2026, 1);

	private static final YearMonth FEB = YearMonth.of(2026, 2);

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	private static final YearMonth APR = YearMonth.of(2026, 4);

	private final PotRepository potRepository = mock(PotRepository.class);

	private final BalanceCheckInRepository checkInRepository = mock(BalanceCheckInRepository.class);

	private final EntryRepository entryRepository = mock(EntryRepository.class);

	private final AllocationRepository allocationRepository = mock(AllocationRepository.class);

	private final PotService potService = new PotService(this.potRepository, this.checkInRepository,
			this.entryRepository, this.allocationRepository);

	@Test
	void archivedPotIsHiddenFromItsArchiveMonthOnwards() {
		Pot pot = pot(PotKind.SAVINGS, "100.00", JAN);
		pot.setArchivedFrom(MAR);
		given(this.potRepository.findAllByOrderById()).willReturn(List.of(pot));
		assertThat(this.potService.monthOf(FEB).pots()).hasSize(1);
		assertThat(this.potService.monthOf(MAR).pots()).isEmpty();
		assertThat(this.potService.monthOf(APR).pots()).isEmpty();
	}

	@Test
	void potIsHiddenBeforeItsOpeningMonth() {
		given(this.potRepository.findAllByOrderById()).willReturn(List.of(pot(PotKind.SAVINGS, "100.00", FEB)));
		assertThat(this.potService.monthOf(JAN).pots()).isEmpty();
		assertThat(this.potService.monthOf(FEB).pots()).hasSize(1);
	}

	@Test
	void checkInBeforeTheOpeningMonthIsRejected() {
		given(this.potRepository.findById(1L)).willReturn(Optional.of(pot(PotKind.SAVINGS, "10.00", FEB)));
		CheckInRequest request = new CheckInRequest(BigDecimal.ONE);
		assertThatThrownBy(() -> this.potService.checkIn(1L, JAN, request)).isInstanceOfSatisfying(
				ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void checkingInTwiceInAMonthReplacesTheBalance() {
		BalanceCheckIn existing = new BalanceCheckIn(1L, MAR, new BigDecimal("4000.00"));
		given(this.potRepository.findById(1L)).willReturn(Optional.of(pot(PotKind.LOAN, "5000.00", JAN)));
		given(this.checkInRepository.findByPotIdAndMonth(1L, MAR)).willReturn(Optional.of(existing));
		given(this.checkInRepository.save(existing)).willReturn(existing);
		CheckInResponse response = this.potService.checkIn(1L, MAR, new CheckInRequest(new BigDecimal("3900.00")));
		assertThat(response.balance()).isEqualByComparingTo("3900.00");
	}

	@Test
	void archivingBeforeTheOpeningMonthIsRejected() {
		given(this.potRepository.findById(1L)).willReturn(Optional.of(pot(PotKind.SAVINGS, "10.00", FEB)));
		UpdatePotRequest request = new UpdatePotRequest("Pot", Account.AIB, BigDecimal.TEN, FEB, JAN);
		assertThatThrownBy(() -> this.potService.update(1L, request)).isInstanceOfSatisfying(
				ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void requireExistsRejectsAMissingPot() {
		given(this.potRepository.existsById(9L)).willReturn(false);
		assertThatThrownBy(() -> this.potService.requireExists(9L)).isInstanceOfSatisfying(
				ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void removingACheckInThatIsntThereIsNotFound() {
		given(this.checkInRepository.findByPotIdAndMonth(1L, MAR)).willReturn(Optional.empty());
		assertThatThrownBy(() -> this.potService.deleteCheckIn(1L, MAR)).isInstanceOfSatisfying(
				ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void removingACheckInDeletesIt() {
		BalanceCheckIn checkIn = new BalanceCheckIn(1L, MAR, new BigDecimal("4000.00"));
		given(this.checkInRepository.findByPotIdAndMonth(1L, MAR)).willReturn(Optional.of(checkIn));
		this.potService.deleteCheckIn(1L, MAR);
		verify(this.checkInRepository).delete(checkIn);
	}

	private static Pot pot(PotKind kind, String openingBalance, YearMonth openingMonth) {
		Pot pot = new Pot("Pot", kind, Account.AIB, new BigDecimal(openingBalance), openingMonth);
		ReflectionTestUtils.setField(pot, "id", 1L);
		return pot;
	}

}
