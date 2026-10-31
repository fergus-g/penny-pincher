package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link EntryRepository#findActiveIn} against the real MySQL database. Each test
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

	private Long save(String name, YearMonth startMonth, YearMonth endMonth) {
		Entry entry = new Entry(EntryType.EXPENSE, "Test", name, new BigDecimal("10.00"), startMonth, endMonth);
		return this.entryRepository.save(entry).getId();
	}

	private List<Long> activeIn(YearMonth month) {
		return this.entryRepository.findActiveIn(month).stream().map(Entry::getId).toList();
	}

}
