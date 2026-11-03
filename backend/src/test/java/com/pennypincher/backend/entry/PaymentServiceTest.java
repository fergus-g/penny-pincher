package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PaymentService}, with "now" fixed in November 2026 and the repositories
 * mocked.
 */
class PaymentServiceTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);

	private static final YearMonth NOV = YearMonth.of(2026, 11);

	private static final YearMonth DEC = YearMonth.of(2026, 12);

	private final PaymentRepository paymentRepository = mock(PaymentRepository.class);

	private final EntryRepository entryRepository = mock(EntryRepository.class);

	private final PaymentService paymentService = new PaymentService(this.paymentRepository, this.entryRepository,
			Clock.fixed(Instant.parse("2026-11-03T09:00:00Z"), ZoneId.of("Europe/Dublin")));

	@Test
	void payTicksAnAibExpenseThisMonth() {
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(expense(1L, Account.AIB)));
		this.paymentService.pay(1L, NOV);
		verify(this.paymentRepository).save(any(Payment.class));
	}

	@Test
	void payingTwiceSavesOnce() {
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(expense(1L, Account.AIB)));
		given(this.paymentRepository.existsByEntryIdAndMonth(1L, NOV)).willReturn(true);
		this.paymentService.pay(1L, NOV);
		verify(this.paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	void payRejectsARevolutExpense() {
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(expense(1L, Account.REVOLUT)));
		assertStatus(() -> this.paymentService.pay(1L, NOV), HttpStatus.BAD_REQUEST);
	}

	@Test
	void payRejectsIncome() {
		Entry salary = new Entry(EntryType.INCOME, "Salary", null, BigDecimal.TEN, null, OCT, null);
		ReflectionTestUtils.setField(salary, "id", 1L);
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(salary));
		assertStatus(() -> this.paymentService.pay(1L, NOV), HttpStatus.BAD_REQUEST);
	}

	@Test
	void payRejectsAnyMonthButThisOne() {
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(expense(1L, Account.AIB)));
		assertStatus(() -> this.paymentService.pay(1L, OCT), HttpStatus.BAD_REQUEST);
		assertStatus(() -> this.paymentService.pay(1L, DEC), HttpStatus.BAD_REQUEST);
	}

	@Test
	void payRejectsAnExpenseNotActiveThisMonth() {
		Entry oneOff = new Entry(EntryType.EXPENSE, "Tech", null, BigDecimal.TEN, Account.AIB, OCT, OCT);
		ReflectionTestUtils.setField(oneOff, "id", 1L);
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(oneOff));
		assertStatus(() -> this.paymentService.pay(1L, NOV), HttpStatus.BAD_REQUEST);
	}

	@Test
	void payUnknownEntryIs404() {
		given(this.entryRepository.findById(9L)).willReturn(Optional.empty());
		assertStatus(() -> this.paymentService.pay(9L, NOV), HttpStatus.NOT_FOUND);
	}

	@Test
	void unpayDeletesThisMonthsPayment() {
		this.paymentService.unpay(1L, NOV);
		verify(this.paymentRepository).deleteByEntryIdAndMonth(1L, NOV);
	}

	@Test
	void unpayRejectsAnyMonthButThisOne() {
		assertStatus(() -> this.paymentService.unpay(1L, OCT), HttpStatus.BAD_REQUEST);
	}

	@Test
	void thisMonthOnlyTickedExpensesArePaid() {
		given(this.paymentRepository.findByMonth(NOV)).willReturn(List.of(new Payment(1L, NOV)));
		Function<Entry, Boolean> paid = this.paymentService.paidIn(NOV);
		assertThat(paid.apply(expense(1L, Account.AIB))).isTrue();
		assertThat(paid.apply(expense(2L, Account.AIB))).isFalse();
		assertThat(paid.apply(expense(3L, Account.REVOLUT))).isNull();
	}

	@Test
	void earlierMonthsAreFullyPaid() {
		Function<Entry, Boolean> paid = this.paymentService.paidIn(OCT);
		assertThat(paid.apply(expense(2L, Account.AIB))).isTrue();
		assertThat(paid.apply(expense(3L, Account.REVOLUT))).isNull();
		verify(this.paymentRepository, never()).findByMonth(any(YearMonth.class));
	}

	@Test
	void laterMonthsAreUnpaid() {
		assertThat(this.paymentService.paidIn(DEC).apply(expense(1L, Account.AIB))).isFalse();
	}

	private static Entry expense(Long id, Account account) {
		Entry entry = new Entry(EntryType.EXPENSE, "Bills", null, new BigDecimal("50.00"), account, OCT, null);
		ReflectionTestUtils.setField(entry, "id", id);
		return entry;
	}

	private static void assertStatus(Runnable action, HttpStatus status) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(status));
	}

}
