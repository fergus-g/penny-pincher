package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.entry.Entry;
import com.pennypincher.backend.entry.EntryRepository;
import com.pennypincher.backend.entry.EntryType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the V3 tables and constraints against the real MySQL database. Each test rolls back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PotRepositoryTest {

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private BalanceCheckInRepository checkInRepository;

	@Autowired
	private EntryRepository entryRepository;

	@Test
	void aPotWithAContributionIsKnownToHaveOne() {
		Long potId = savePot();
		Entry repayment = new Entry(EntryType.EXPENSE, "Loans", null, BigDecimal.TEN, Account.AIB, MAR, null);
		repayment.setPotId(potId);
		this.entryRepository.save(repayment);
		assertThat(this.entryRepository.existsByPotId(potId)).isTrue();
	}

	@Test
	void incomeCannotPayIntoAPot() {
		Entry salary = new Entry(EntryType.INCOME, "Salary", null, BigDecimal.TEN, Account.AIB, MAR, null);
		salary.setPotId(savePot());
		assertThatThrownBy(() -> this.entryRepository.saveAndFlush(salary))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aPotHasOneCheckInPerMonth() {
		Long potId = savePot();
		this.checkInRepository.saveAndFlush(new BalanceCheckIn(potId, MAR, BigDecimal.ONE));
		assertThatThrownBy(() -> this.checkInRepository.saveAndFlush(new BalanceCheckIn(potId, MAR, BigDecimal.TWO)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Long savePot() {
		return this.potRepository.saveAndFlush(new Pot("Test pot", PotKind.LOAN, Account.AIB, BigDecimal.TEN, MAR))
			.getId();
	}

}
