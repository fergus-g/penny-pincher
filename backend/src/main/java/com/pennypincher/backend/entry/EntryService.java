package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
				totalExpenses, totalIncome.subtract(totalExpenses), transfers(entries), expensesByCategory(entries));
	}

	/** Every category used on this type of Entry, variants merged, in alphabetical order. */
	@Transactional(readOnly = true)
	public List<String> getCategories(EntryType type) {
		return this.entryRepository.findCategories(type)
			.stream()
			.collect(Collectors.groupingBy(EntryService::categoryKey, TreeMap::new, Collectors.toList()))
			.values()
			.stream()
			.map(EntryService::mostCommonSpelling)
			.toList();
	}

	/** The month's Expenses totalled per category, largest first. */
	private static List<CategoryTotal> expensesByCategory(List<Entry> entries) {
		return entries.stream()
			.filter((entry) -> entry.getType() == EntryType.EXPENSE)
			.collect(Collectors.groupingBy((entry) -> categoryKey(entry.getCategory()), LinkedHashMap::new,
					Collectors.toList()))
			.values()
			.stream()
			.map((group) -> new CategoryTotal(mostCommonSpelling(group.stream().map(Entry::getCategory).toList()),
					total(group, EntryType.EXPENSE)))
			.sorted(Comparator.comparing(CategoryTotal::amount).reversed())
			.toList();
	}

	/** "Groceries" and "groceries " are one category. */
	private static String categoryKey(String category) {
		return category.trim().toLowerCase(Locale.ROOT);
	}

	/** The spelling used most among a category's variants, trimmed; a tie goes to the first seen. */
	private static String mostCommonSpelling(List<String> spellings) {
		return spellings.stream()
			.map(String::trim)
			.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
			.entrySet()
			.stream()
			.max(Map.Entry.comparingByValue())
			.orElseThrow()
			.getKey();
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
