package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pennypincher.backend.allocation.Allocation;
import com.pennypincher.backend.allocation.AllocationSummary;
import com.pennypincher.backend.entry.Entry;
import com.pennypincher.backend.entry.EntryType;

/**
 * Works out every Pot's balance at the end of a month, and how that month's Allocation splits
 * the Remainder, by replaying the months since each Pot's last known balance (ADR 0001).
 *
 * <p>
 * Each Pot starts from its latest Balance Check-in on or before the month, or else its opening
 * balance; both are end-of-month figures. Each later month then applies, in order:
 * <ol>
 * <li>Contributions: they add to a Savings Pot and pay down a Debt Pot, never below zero.</li>
 * <li>The Allocation in force: Debt Repayment pays down its Debt Pot, and anything the Debt Pot
 * can't take once cleared goes to its Savings Pot along with the Savings.</li>
 * </ol>
 * Pots are replayed together because that overflow links them.
 */
final class MonthLedger {

	private MonthLedger() {
	}

	/**
	 * @param pots every Pot, in display order
	 * @param allocations every Allocation, oldest first
	 */
	static PotMonth run(YearMonth month, List<Pot> pots, List<BalanceCheckIn> checkIns, List<Entry> entries,
			List<Allocation> allocations) {
		Map<Long, Running> running = new LinkedHashMap<>();
		for (Pot pot : pots) {
			if (!pot.getOpeningMonth().isAfter(month)) {
				running.put(pot.getId(), Running.start(pot, month, checkIns));
			}
		}
		YearMonth from = running.values()
			.stream()
			.map((pot) -> pot.from.plusMonths(1))
			.filter((first) -> !first.isAfter(month))
			.min(Comparator.naturalOrder())
			.orElse(month);
		Split split = null;
		for (YearMonth current = from; !current.isAfter(month); current = current.plusMonths(1)) {
			for (Running pot : running.values()) {
				if (pot.isLiveIn(current)) {
					pot.contribute(contributions(pot.pot, entries, current), current);
				}
			}
			split = allocate(current, entries, allocations, running);
		}
		List<PotBalance> balances = running.values()
			.stream()
			.filter((pot) -> pot.pot.isActiveIn(month))
			.map(Running::toBalance)
			.toList();
		return new PotMonth(balances, (split != null) ? split.summary(pots, running) : null);
	}

	/** Applies the month's Allocation to the Pots it targets; null with no Allocation in force. */
	private static Split allocate(YearMonth month, List<Entry> entries, List<Allocation> allocations,
			Map<Long, Running> running) {
		Optional<Allocation> inForce = allocations.stream()
			.filter((allocation) -> !allocation.getEffectiveFrom().isAfter(month))
			.reduce((earlier, later) -> later);
		if (inForce.isEmpty()) {
			return null;
		}
		Allocation allocation = inForce.get();
		List<Entry> active = entries.stream().filter((entry) -> entry.isActiveIn(month)).toList();
		BigDecimal remainder = total(active, EntryType.INCOME).subtract(total(active, EntryType.EXPENSE));
		BigDecimal planned = allocation.getSavings().add(allocation.getAllowance());
		BigDecimal debtRepayment = remainder.subtract(planned).max(BigDecimal.ZERO);
		BigDecimal debtPaid = debtRepayment;
		Running debtPot = live(running, allocation.getDebtPotId(), month);
		if (debtPot != null) {
			debtPaid = debtPot.payDown(debtRepayment, month);
		}
		BigDecimal overflow = debtRepayment.subtract(debtPaid);
		Running savingsPot = live(running, allocation.getSavingsPotId(), month);
		if (savingsPot != null) {
			savingsPot.add(allocation.getSavings().add(overflow));
		}
		return new Split(allocation, debtRepayment, planned.compareTo(remainder) > 0, debtPaid, overflow);
	}

	/** The Pot's running balance if it's being replayed in {@code month}; null otherwise. */
	private static Running live(Map<Long, Running> running, Long potId, YearMonth month) {
		Running pot = (potId != null) ? running.get(potId) : null;
		return (pot != null && pot.isLiveIn(month)) ? pot : null;
	}

	private static BigDecimal contributions(Pot pot, List<Entry> entries, YearMonth month) {
		return entries.stream()
			.filter((entry) -> pot.getId().equals(entry.getPotId()) && entry.isActiveIn(month))
			.map(Entry::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal total(List<Entry> entries, EntryType type) {
		return entries.stream()
			.filter((entry) -> entry.getType() == type)
			.map(Entry::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static Optional<Pot> find(List<Pot> pots, Long id) {
		return (id != null) ? pots.stream().filter((pot) -> pot.getId().equals(id)).findFirst() : Optional.empty();
	}

	/** One Pot's balance as the months are replayed. */
	private static final class Running {

		private final Pot pot;

		/** The month whose end-of-month balance we start from; replay begins the month after. */
		private final YearMonth from;

		private final boolean checkedIn;

		private BigDecimal balance;

		/** When a Debt Pot last reached zero. */
		private YearMonth paidOffIn;

		private Running(Pot pot, YearMonth from, BigDecimal balance, boolean checkedIn) {
			this.pot = pot;
			this.from = from;
			this.balance = balance;
			this.checkedIn = checkedIn;
			if (pot.getKind().isDebt() && balance.signum() == 0) {
				this.paidOffIn = from;
			}
		}

		static Running start(Pot pot, YearMonth month, List<BalanceCheckIn> checkIns) {
			Optional<BalanceCheckIn> latest = checkIns.stream()
				.filter((checkIn) -> checkIn.getPotId().equals(pot.getId()) && !checkIn.getMonth().isAfter(month))
				.max(Comparator.comparing(BalanceCheckIn::getMonth));
			return latest
				.map((checkIn) -> new Running(pot, checkIn.getMonth(), checkIn.getBalance(),
						checkIn.getMonth().equals(month)))
				.orElseGet(() -> new Running(pot, pot.getOpeningMonth(), pot.getOpeningBalance(), false));
		}

		boolean isLiveIn(YearMonth month) {
			return this.from.isBefore(month);
		}

		void contribute(BigDecimal amount, YearMonth month) {
			if (this.pot.getKind().isDebt()) {
				payDown(amount, month);
			}
			else {
				add(amount);
			}
		}

		void add(BigDecimal amount) {
			this.balance = this.balance.add(amount);
		}

		/** Pays off up to {@code amount}, never below zero; returns how much it took. */
		BigDecimal payDown(BigDecimal amount, YearMonth month) {
			BigDecimal taken = amount.min(this.balance);
			if (taken.signum() > 0 && taken.compareTo(this.balance) == 0) {
				this.paidOffIn = month;
			}
			this.balance = this.balance.subtract(taken);
			return taken;
		}

		PotBalance toBalance() {
			return new PotBalance(this.pot.getId(), this.pot.getName(), this.pot.getKind(), this.pot.getAccount(),
					this.balance, this.checkedIn);
		}

	}

	/** The Allocation's split of one month's Remainder. */
	private record Split(Allocation allocation, BigDecimal debtRepayment, boolean shortfall, BigDecimal debtPaid,
			BigDecimal overflow) {

		AllocationSummary summary(List<Pot> pots, Map<Long, Running> running) {
			Optional<Pot> savingsPot = find(pots, this.allocation.getSavingsPotId());
			Optional<Pot> debtPot = find(pots, this.allocation.getDebtPotId());
			Running debt = debtPot.map((pot) -> running.get(pot.getId())).orElse(null);
			YearMonth paidOffIn = (debt != null && debt.balance.signum() == 0) ? debt.paidOffIn : null;
			return new AllocationSummary(this.allocation.getEffectiveFrom(), this.allocation.getSavings(),
					this.allocation.getAllowance(), this.debtRepayment,
					this.shortfall, savingsPot.map(Pot::getId).orElse(null),
					savingsPot.map(Pot::getName).orElse(null), debtPot.map(Pot::getId).orElse(null),
					debtPot.map(Pot::getName).orElse(null), this.overflow, paidOffIn);
		}

	}

}
