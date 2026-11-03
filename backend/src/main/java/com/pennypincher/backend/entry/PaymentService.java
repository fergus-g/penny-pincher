package com.pennypincher.backend.entry;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ticks AIB Expenses as paid. Only the current month can be ticked: earlier months count as fully
 * paid and later months as unpaid (ADR 0003).
 */
@Service
public class PaymentService {

	private final PaymentRepository paymentRepository;

	private final EntryRepository entryRepository;

	private final Clock clock;

	public PaymentService(PaymentRepository paymentRepository, EntryRepository entryRepository, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.entryRepository = entryRepository;
		this.clock = clock;
	}

	/** Ticks the Expense as paid this month; ticking it again changes nothing. */
	@Transactional
	public void pay(Long entryId, YearMonth month) {
		requireCurrentMonth(month);
		Entry entry = this.entryRepository.findById(entryId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry " + entryId + " not found"));
		if (!canBePaid(entry)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only AIB Expenses can be ticked as paid");
		}
		if (!entry.isActiveIn(month)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That Expense isn't active this month");
		}
		if (!this.paymentRepository.existsByEntryIdAndMonth(entryId, month)) {
			this.paymentRepository.save(new Payment(entryId, month));
		}
	}

	/** Unticks the Expense this month; unticking one that isn't ticked changes nothing. */
	@Transactional
	public void unpay(Long entryId, YearMonth month) {
		requireCurrentMonth(month);
		this.paymentRepository.deleteByEntryIdAndMonth(entryId, month);
	}

	/**
	 * Whether each Entry is paid in {@code month}: null for an Entry that can't have a Payment, true
	 * for any month before this one, false for any month after it, and this month whether it's ticked.
	 */
	@Transactional(readOnly = true)
	public Function<Entry, Boolean> paidIn(YearMonth month) {
		YearMonth now = YearMonth.now(this.clock);
		Set<Long> ticked = month.equals(now) ? this.paymentRepository.findByMonth(month)
			.stream()
			.map(Payment::getEntryId)
			.collect(Collectors.toSet()) : Set.of();
		return (entry) -> canBePaid(entry) ? (month.isBefore(now) || ticked.contains(entry.getId())) : null;
	}

	/** Only an Expense out of an Account whose Payments are tracked: AIB. */
	static boolean canBePaid(Entry entry) {
		return entry.getType() == EntryType.EXPENSE && entry.getAccount() != null
				&& entry.getAccount().tracksPayments();
	}

	private void requireCurrentMonth(YearMonth month) {
		if (!month.equals(YearMonth.now(this.clock))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only this month's Expenses can be ticked");
		}
	}

}
