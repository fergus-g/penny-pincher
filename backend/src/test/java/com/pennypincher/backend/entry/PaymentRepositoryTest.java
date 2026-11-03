package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the Payment queries and the migration's constraints against the real MySQL database. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest {

	private static final YearMonth NOV = YearMonth.of(2026, 11);

	@Autowired
	private EntryRepository entryRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Test
	void findsAndDeletesByEntryAndMonth() {
		Long rent = saveExpense();
		this.paymentRepository.saveAndFlush(new Payment(rent, NOV));
		assertThat(this.paymentRepository.existsByEntryIdAndMonth(rent, NOV)).isTrue();
		assertThat(this.paymentRepository.existsByEntryIdAndMonth(rent, NOV.plusMonths(1))).isFalse();
		assertThat(this.paymentRepository.findByMonth(NOV)).extracting(Payment::getEntryId).contains(rent);
		this.paymentRepository.deleteByEntryIdAndMonth(rent, NOV);
		assertThat(this.paymentRepository.existsByEntryIdAndMonth(rent, NOV)).isFalse();
	}

	@Test
	void anExpenseIsPaidOncePerMonth() {
		Long rent = saveExpense();
		this.paymentRepository.saveAndFlush(new Payment(rent, NOV));
		assertThatThrownBy(() -> this.paymentRepository.saveAndFlush(new Payment(rent, NOV)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingTheExpenseDeletesItsPayments() {
		Long rent = saveExpense();
		this.paymentRepository.saveAndFlush(new Payment(rent, NOV));
		this.entryRepository.deleteById(rent);
		this.entryRepository.flush();
		assertThat(this.paymentRepository.findByMonth(NOV)).extracting(Payment::getEntryId).doesNotContain(rent);
	}

	private Long saveExpense() {
		return this.entryRepository
			.saveAndFlush(new Entry(EntryType.EXPENSE, "Housing", "Rent", new BigDecimal("1000.00"), Account.AIB,
					NOV, null))
			.getId();
	}

}
