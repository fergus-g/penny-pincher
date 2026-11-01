package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EntryService {

	private final EntryRepository entryRepository;

	public EntryService(EntryRepository entryRepository) {
		this.entryRepository = entryRepository;
	}

	@Transactional
	public EntryResponse create(CreateEntryRequest request) {
		YearMonth endMonth = request.oneOff() ? request.startMonth() : null;
		Entry entry = new Entry(request.type(), request.category(), request.name(), request.amount(),
				request.account(), request.startMonth(), endMonth);
		return EntryResponse.from(this.entryRepository.save(entry));
	}

	@Transactional
	public EntryResponse update(Long id, UpdateEntryRequest request) {
		Entry entry = findEntry(id);
		entry.setCategory(request.category());
		entry.setName(request.name());
		entry.setAmount(request.amount());
		entry.setAccount(request.account());
		entry.setEndMonth(request.oneOff() ? entry.getStartMonth() : null);
		return EntryResponse.from(entry);
	}

	@Transactional
	public void delete(Long id) {
		this.entryRepository.delete(findEntry(id));
	}

	@Transactional(readOnly = true)
	public MonthSummaryResponse getMonth(YearMonth month) {
		List<Entry> entries = this.entryRepository.findActiveIn(month);
		BigDecimal totalIncome = total(entries, EntryType.INCOME);
		BigDecimal totalExpenses = total(entries, EntryType.EXPENSE);
		return new MonthSummaryResponse(month, entries.stream().map(EntryResponse::from).toList(), totalIncome,
				totalExpenses, totalIncome.subtract(totalExpenses), transfers(entries));
	}

	/** One Transfer per non-home Account: its Expenses minus its Income. Zero when it balances. */
	private static List<Transfer> transfers(List<Entry> entries) {
		return Arrays.stream(Account.values()).filter((account) -> account != Account.HOME).map((account) -> {
			List<Entry> onAccount = entries.stream().filter((entry) -> entry.getAccount() == account).toList();
			return new Transfer(account,
					total(onAccount, EntryType.EXPENSE).subtract(total(onAccount, EntryType.INCOME)));
		}).toList();
	}

	private Entry findEntry(Long id) {
		return this.entryRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry " + id + " not found"));
	}

	private static BigDecimal total(List<Entry> entries, EntryType type) {
		return entries.stream()
			.filter((entry) -> entry.getType() == type)
			.map(Entry::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
