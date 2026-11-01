package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the Entry queries and the migrations' constraints against the real MySQL database. Each test
 * rolls back, and assertions only look at the entries saved here, so existing rows in
 * the database don't affect the results.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntryRepositoryTest {

	private static final YearMonth FEB = YearMonth.of(2026, 2);

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	private static final YearMonth JUN = YearMonth.of(2026, 6);

	private static final YearMonth JUL = YearMonth.of(2026, 7);

	@Autowired
	private EntryRepository entryRepository;

	private Long oneOff;

	private Long recurring;

	private Long cancelled;

	@BeforeEach
	void saveEntries() {
		this.oneOff = save("One-off", MAR, MAR);
		this.recurring = save("Recurring", MAR, null);
		this.cancelled = save("Cancelled", MAR, JUN);
	}

	@Test
	void monthBeforeStartIncludesNothing() {
		assertThat(activeIn(FEB)).doesNotContain(this.oneOff, this.recurring, this.cancelled);
	}

	@Test
	void startMonthIncludesAllThree() {
		assertThat(activeIn(MAR)).contains(this.oneOff, this.recurring, this.cancelled);
	}

	@Test
	void cancelledEntryIsStillIncludedInItsEndMonth() {
		List<Long> june = activeIn(JUN);
		assertThat(june).contains(this.recurring, this.cancelled);
		assertThat(june).doesNotContain(this.oneOff);
	}

	@Test
	void afterEndMonthOnlyRecurringRemains() {
		List<Long> july = activeIn(JUL);
		assertThat(july).contains(this.recurring);
		assertThat(july).doesNotContain(this.oneOff, this.cancelled);
	}

	/** V5: Income has no Account, and every Expense has one. */
	@Test
	void onlyExpensesHaveAnAccount() {
		assertThatThrownBy(() -> this.entryRepository
			.saveAndFlush(new Entry(EntryType.INCOME, "Salary", null, BigDecimal.TEN, Account.AIB, MAR, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.entryRepository
			.saveAndFlush(new Entry(EntryType.EXPENSE, "Rent", null, BigDecimal.TEN, null, MAR, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findCategoriesReturnsOnePerEntryOfThatTypeOnly() {
		this.entryRepository.save(new Entry(EntryType.INCOME, "Salary-test", null, BigDecimal.TEN, null, MAR, null));
		List<String> categories = this.entryRepository.findCategories(EntryType.EXPENSE);
		assertThat(categories).filteredOn("Test"::equals).hasSizeGreaterThanOrEqualTo(3);
		assertThat(categories).doesNotContain("Salary-test");
	}

	private Long save(String name, YearMonth startMonth, YearMonth endMonth) {
		Entry entry = new Entry(EntryType.EXPENSE, "Test", name, new BigDecimal("10.00"), Account.AIB, startMonth,
				endMonth);
		return this.entryRepository.save(entry).getId();
	}

	private List<Long> activeIn(YearMonth month) {
		return this.entryRepository.findActiveIn(month).stream().map(Entry::getId).toList();
	}

}
