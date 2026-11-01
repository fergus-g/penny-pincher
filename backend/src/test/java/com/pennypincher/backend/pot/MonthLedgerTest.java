package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import com.pennypincher.backend.allocation.Allocation;
import com.pennypincher.backend.allocation.AllocationSummary;
import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.entry.Entry;
import com.pennypincher.backend.entry.EntryType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the balance and Allocation rules in {@link MonthLedger}, a pure function: no mocks, just
 * Pots, Check-ins, Entries and Allocations in, balances and the month's split out.
 */
class MonthLedgerTest {

	private static final YearMonth JAN = YearMonth.of(2026, 1);

	private static final YearMonth FEB = YearMonth.of(2026, 2);

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	private static final YearMonth APR = YearMonth.of(2026, 4);

	private final List<Pot> pots = new ArrayList<>();

	private final List<BalanceCheckIn> checkIns = new ArrayList<>();

	private final List<Entry> entries = new ArrayList<>();

	private final List<Allocation> allocations = new ArrayList<>();

	// Contributions and Check-ins (slice 3)

	@Test
	void openingBalanceIsTheEndOfOpeningMonthBalance() {
		Pot loan = pot(1L, PotKind.LOAN, "5000.00", JAN, Account.AIB);
		contribution(loan, "250.00", JAN, null);
		assertThat(balance(loan, JAN)).isEqualByComparingTo("5000.00");
		assertThat(balance(loan, FEB)).isEqualByComparingTo("4750.00");
		assertThat(balance(loan, MAR)).isEqualByComparingTo("4500.00");
	}

	@Test
	void checkInReplacesTheCalculatedBalanceFromItsMonth() {
		Pot loan = pot(1L, PotKind.LOAN, "5000.00", JAN, Account.AIB);
		contribution(loan, "250.00", JAN, null);
		this.checkIns.add(new BalanceCheckIn(1L, MAR, new BigDecimal("4000.00")));
		assertThat(balance(loan, FEB)).isEqualByComparingTo("4750.00");
		PotBalance march = potBalance(loan, MAR);
		assertThat(march.balance()).isEqualByComparingTo("4000.00");
		assertThat(march.checkedIn()).isTrue();
		PotBalance april = potBalance(loan, APR);
		assertThat(april.balance()).isEqualByComparingTo("3750.00");
		assertThat(april.checkedIn()).isFalse();
	}

	@Test
	void debtNeverGoesBelowZero() {
		Pot card = pot(1L, PotKind.CREDIT_CARD, "300.00", JAN, Account.AIB);
		contribution(card, "250.00", JAN, null);
		assertThat(balance(card, FEB)).isEqualByComparingTo("50.00");
		assertThat(balance(card, MAR)).isZero();
		assertThat(balance(card, APR)).isZero();
	}

	@Test
	void savingsContributionsAddOnlyInMonthsTheyAreActive() {
		Pot rainyDay = pot(1L, PotKind.SAVINGS, "1000.00", JAN, Account.AIB);
		contribution(rainyDay, "100.00", FEB, null);
		contribution(rainyDay, "50.00", MAR, MAR);
		assertThat(balance(rainyDay, FEB)).isEqualByComparingTo("1100.00");
		assertThat(balance(rainyDay, MAR)).isEqualByComparingTo("1250.00");
		assertThat(balance(rainyDay, APR)).isEqualByComparingTo("1350.00");
	}

	// Allocation (slice 4)

	@Test
	void debtRepaymentIsWhatsLeftAfterSavingsAndAllowance() {
		remainderOf("1000.00", JAN);
		allocate(JAN, "400.00", "200.00", null, null);
		AllocationSummary split = allocation(FEB);
		assertThat(split.debtRepayment()).isEqualByComparingTo("400.00");
		assertThat(split.shortfall()).isFalse();
	}

	@Test
	void savingsAndAllowanceOverTheRemainderIsAShortfallWithNoDebtRepayment() {
		remainderOf("500.00", JAN);
		allocate(JAN, "400.00", "200.00", null, null);
		AllocationSummary split = allocation(FEB);
		assertThat(split.debtRepayment()).isZero();
		assertThat(split.shortfall()).isTrue();
		assertThat(split.savings()).isEqualByComparingTo("400.00");
		assertThat(split.allowance()).isEqualByComparingTo("200.00");
	}

	@Test
	void noAllocationBeforeTheFirstOneStarts() {
		remainderOf("1000.00", JAN);
		allocate(MAR, "400.00", "200.00", null, null);
		assertThat(MonthLedger.run(FEB, this.pots, this.checkIns, this.entries, this.allocations).allocation())
			.isNull();
	}

	@Test
	void changingTheAllocationInMarchLeavesJanuaryAndFebruaryAlone() {
		Pot rainyDay = pot(1L, PotKind.SAVINGS, "0.00", YearMonth.of(2025, 12), Account.AIB);
		remainderOf("1000.00", JAN);
		allocate(JAN, "100.00", "0.00", rainyDay, null);
		BigDecimal febBefore = balance(rainyDay, FEB);
		AllocationSummary febSplitBefore = allocation(FEB);

		allocate(MAR, "300.00", "0.00", rainyDay, null);

		assertThat(balance(rainyDay, JAN)).isEqualByComparingTo("100.00");
		assertThat(balance(rainyDay, FEB)).isEqualByComparingTo(febBefore).isEqualByComparingTo("200.00");
		assertThat(allocation(FEB)).isEqualTo(febSplitBefore);
		assertThat(balance(rainyDay, MAR)).isEqualByComparingTo("500.00");
	}

	@Test
	void debtRepaymentPaysDownTheDebtPotAndSavingsGrowTheSavingsPot() {
		Pot loan = pot(1L, PotKind.LOAN, "5000.00", JAN, Account.AIB);
		Pot rainyDay = pot(2L, PotKind.SAVINGS, "1000.00", JAN, Account.AIB);
		remainderOf("1000.00", JAN);
		allocate(FEB, "400.00", "200.00", rainyDay, loan);
		assertThat(balance(loan, FEB)).isEqualByComparingTo("4600.00");
		assertThat(balance(rainyDay, FEB)).isEqualByComparingTo("1400.00");
		assertThat(balance(loan, MAR)).isEqualByComparingTo("4200.00");
	}

	@Test
	void clearedDebtSendsTheRestToTheSavingsPotAndSaysWhenItWasPaidOff() {
		Pot card = pot(1L, PotKind.CREDIT_CARD, "300.00", JAN, Account.AIB);
		Pot rainyDay = pot(2L, PotKind.SAVINGS, "1000.00", JAN, Account.AIB);
		remainderOf("1000.00", JAN);
		allocate(FEB, "400.00", "200.00", rainyDay, card);

		assertThat(balance(card, FEB)).isZero();
		assertThat(balance(rainyDay, FEB)).isEqualByComparingTo("1500.00");
		AllocationSummary february = allocation(FEB);
		assertThat(february.debtOverflow()).isEqualByComparingTo("100.00");
		assertThat(february.debtPaidOffIn()).isEqualTo(FEB);

		AllocationSummary march = allocation(MAR);
		assertThat(march.debtOverflow()).isEqualByComparingTo("400.00");
		assertThat(march.debtPaidOffIn()).isEqualTo(FEB);
		assertThat(balance(rainyDay, MAR)).isEqualByComparingTo("2300.00");
	}

	@Test
	void untargetedSharesAreUnassignedAndMoveNoBalance() {
		Pot loan = pot(1L, PotKind.LOAN, "5000.00", JAN, Account.AIB);
		Pot rainyDay = pot(2L, PotKind.SAVINGS, "1000.00", JAN, Account.AIB);
		remainderOf("1000.00", JAN);
		allocate(FEB, "400.00", "200.00", null, null);
		PotMonth february = MonthLedger.run(FEB, this.pots, this.checkIns, this.entries, this.allocations);
		assertThat(february.allocation().savingsPotId()).isNull();
		assertThat(february.allocation().debtPotId()).isNull();
		assertThat(balance(loan, FEB)).isEqualByComparingTo("5000.00");
		assertThat(balance(rainyDay, FEB)).isEqualByComparingTo("1000.00");
	}

	private BigDecimal balance(Pot pot, YearMonth month) {
		return potBalance(pot, month).balance();
	}

	private PotBalance potBalance(Pot pot, YearMonth month) {
		return MonthLedger.run(month, this.pots, this.checkIns, this.entries, this.allocations)
			.pots()
			.stream()
			.filter((balance) -> balance.id().equals(pot.getId()))
			.findFirst()
			.orElseThrow();
	}

	private AllocationSummary allocation(YearMonth month) {
		return MonthLedger.run(month, this.pots, this.checkIns, this.entries, this.allocations).allocation();
	}

	private Pot pot(Long id, PotKind kind, String openingBalance, YearMonth openingMonth, Account account) {
		Pot pot = new Pot("Pot " + id, kind, account, new BigDecimal(openingBalance), openingMonth);
		ReflectionTestUtils.setField(pot, "id", id);
		this.pots.add(pot);
		return pot;
	}

	private void contribution(Pot pot, String amount, YearMonth startMonth, YearMonth endMonth) {
		Entry entry = new Entry(EntryType.EXPENSE, "Pots", null, new BigDecimal(amount), Account.AIB, startMonth,
				endMonth);
		entry.setPotId(pot.getId());
		this.entries.add(entry);
	}

	/** A recurring Income from {@code month}, so that's each month's Remainder. */
	private void remainderOf(String amount, YearMonth month) {
		this.entries.add(new Entry(EntryType.INCOME, "Salary", null, new BigDecimal(amount), null, month, null));
	}

	private void allocate(YearMonth from, String savings, String allowance, Pot savingsPot, Pot debtPot) {
		Allocation allocation = new Allocation(from);
		allocation.setSavings(new BigDecimal(savings));
		allocation.setAllowance(new BigDecimal(allowance));
		allocation.setSavingsPotId((savingsPot != null) ? savingsPot.getId() : null);
		allocation.setDebtPotId((debtPot != null) ? debtPot.getId() : null);
		this.allocations.add(allocation);
	}

}
