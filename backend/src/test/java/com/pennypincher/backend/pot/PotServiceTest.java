package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.entry.Entry;
import com.pennypincher.backend.entry.EntryRepository;
import com.pennypincher.backend.entry.EntryType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PotService}, with the repositories mocked. The balance rule is
 * exercised through {@link PotService#balanceIn}, one month at a time.
 */
class PotServiceTest {

	private static final YearMonth JAN = YearMonth.of(2026, 1);

	private static final YearMonth FEB = YearMonth.of(2026, 2);

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	private static final YearMonth APR = YearMonth.of(2026, 4);

	private final PotRepository potRepository = mock(PotRepository.class);

	private final BalanceCheckInRepository checkInRepository = mock(BalanceCheckInRepository.class);

	private final EntryRepository entryRepository = mock(EntryRepository.class);

	private final PotService potService = new PotService(this.potRepository, this.checkInRepository,
			this.entryRepository);

	@Test
	void openingBalanceIsTheEndOfOpeningMonthBalance() {
		Pot loan = pot(PotKind.LOAN, "5000.00", JAN);
		List<Entry> repayments = List.of(contribution("250.00", JAN, null));
		assertThat(balance(loan, JAN, List.of(), repayments)).isEqualByComparingTo("5000.00");
		assertThat(balance(loan, FEB, List.of(), repayments)).isEqualByComparingTo("4750.00");
		assertThat(balance(loan, MAR, List.of(), repayments)).isEqualByComparingTo("4500.00");
	}

	@Test
	void checkInReplacesTheCalculatedBalanceFromItsMonth() {
		Pot loan = pot(PotKind.LOAN, "5000.00", JAN);
		List<Entry> repayments = List.of(contribution("250.00", JAN, null));
		List<BalanceCheckIn> checkIns = List.of(new BalanceCheckIn(1L, MAR, new BigDecimal("4000.00")));
		assertThat(balance(loan, FEB, checkIns, repayments)).isEqualByComparingTo("4750.00");
		PotBalance march = PotService.balanceIn(loan, MAR, checkIns, repayments);
		assertThat(march.balance()).isEqualByComparingTo("4000.00");
		assertThat(march.checkedIn()).isTrue();
		PotBalance april = PotService.balanceIn(loan, APR, checkIns, repayments);
		assertThat(april.balance()).isEqualByComparingTo("3750.00");
		assertThat(april.checkedIn()).isFalse();
	}

	@Test
	void debtNeverGoesBelowZero() {
		Pot card = pot(PotKind.CREDIT_CARD, "300.00", JAN);
		List<Entry> repayments = List.of(contribution("250.00", JAN, null));
		assertThat(balance(card, FEB, List.of(), repayments)).isEqualByComparingTo("50.00");
		assertThat(balance(card, MAR, List.of(), repayments)).isZero();
		assertThat(balance(card, APR, List.of(), repayments)).isZero();
	}

	@Test
	void savingsContributionsAddOnlyInMonthsTheyAreActive() {
		Pot rainyDay = pot(PotKind.SAVINGS, "1000.00", JAN);
		List<Entry> deposits = List.of(contribution("100.00", FEB, null), contribution("50.00", MAR, MAR));
		assertThat(balance(rainyDay, FEB, List.of(), deposits)).isEqualByComparingTo("1100.00");
		assertThat(balance(rainyDay, MAR, List.of(), deposits)).isEqualByComparingTo("1250.00");
		assertThat(balance(rainyDay, APR, List.of(), deposits)).isEqualByComparingTo("1350.00");
	}

	@Test
	void archivedPotIsHiddenFromItsArchiveMonthOnwards() {
		Pot pot = pot(PotKind.SAVINGS, "100.00", JAN);
		pot.setArchivedFrom(MAR);
		given(this.potRepository.findAllByOrderById()).willReturn(List.of(pot));
		assertThat(this.potService.balancesIn(FEB)).hasSize(1);
		assertThat(this.potService.balancesIn(MAR)).isEmpty();
		assertThat(this.potService.balancesIn(APR)).isEmpty();
	}

	@Test
	void potIsHiddenBeforeItsOpeningMonth() {
		given(this.potRepository.findAllByOrderById()).willReturn(List.of(pot(PotKind.SAVINGS, "100.00", FEB)));
		assertThat(this.potService.balancesIn(JAN)).isEmpty();
		assertThat(this.potService.balancesIn(FEB)).hasSize(1);
	}

	@Test
	void deletingAPotWithContributionsIsRejected() {
		given(this.potRepository.findById(1L)).willReturn(Optional.of(pot(PotKind.LOAN, "10.00", JAN)));
		given(this.entryRepository.existsByPotId(1L)).willReturn(true);
		assertThatThrownBy(() -> this.potService.delete(1L)).isInstanceOfSatisfying(ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
		verify(this.potRepository, never()).delete(any(Pot.class));
	}

	@Test
	void deletingAPotWithoutContributionsTakesItsCheckInsWithIt() {
		Pot pot = pot(PotKind.LOAN, "10.00", JAN);
		given(this.potRepository.findById(1L)).willReturn(Optional.of(pot));
		this.potService.delete(1L);
		verify(this.checkInRepository).deleteByPotId(1L);
		verify(this.potRepository).delete(pot);
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

	private static BigDecimal balance(Pot pot, YearMonth month, List<BalanceCheckIn> checkIns,
			List<Entry> contributions) {
		return PotService.balanceIn(pot, month, checkIns, contributions).balance();
	}

	private static Pot pot(PotKind kind, String openingBalance, YearMonth openingMonth) {
		return new Pot("Pot", kind, Account.AIB, new BigDecimal(openingBalance), openingMonth);
	}

	private static Entry contribution(String amount, YearMonth startMonth, YearMonth endMonth) {
		return new Entry(EntryType.EXPENSE, "Debt", null, new BigDecimal(amount), Account.AIB, startMonth, endMonth);
	}

}
