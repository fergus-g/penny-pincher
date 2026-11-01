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

import com.pennypincher.backend.pot.PotMonth;
import com.pennypincher.backend.pot.PotService;

@Service
public class EntryService {

	private final EntryRepository entryRepository;

	private final PotService potService;

	public EntryService(EntryRepository entryRepository, PotService potService) {
		this.entryRepository = entryRepository;
		this.potService = potService;
	}

	@Transactional
	public EntryResponse create(CreateEntryRequest request) {
		requireValidAccount(request.type(), request.account());
		requireExpenseOnly(request.type(), request.dueDay(), "a Due Day");
		requireValidPot(request.type(), request.potId());
		YearMonth endMonth = request.oneOff() ? request.startMonth() : null;
		Entry entry = new Entry(request.type(), request.category(), request.name(), request.amount(),
				request.account(), request.startMonth(), endMonth);
		entry.setPotId(request.potId());
		entry.setDueDay(request.dueDay());
		Entry saved = this.entryRepository.save(entry);
		return EntryResponse.from(saved, saved.getStartMonth());
	}

	@Transactional
	public EntryResponse update(Long id, UpdateEntryRequest request) {
		Entry entry = findEntry(id);
		requireValidAccount(entry.getType(), request.account());
		requireExpenseOnly(entry.getType(), request.dueDay(), "a Due Day");
		requireValidPot(entry.getType(), request.potId());
		entry.setCategory(request.category());
		entry.setName(request.name());
		entry.setAmount(request.amount());
		entry.setAccount(request.account());
		entry.setEndMonth(request.oneOff() ? entry.getStartMonth() : null);
		entry.setPotId(request.potId());
		entry.setDueDay(request.dueDay());
		return EntryResponse.from(entry, entry.getStartMonth());
	}

	/** An Expense is paid out of an Account; Income lands outside the app, so it has none. */
	private static void requireValidAccount(EntryType type, Account account) {
		if (type == EntryType.EXPENSE && account == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An Expense needs an Account");
		}
		if (type == EntryType.INCOME && account != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income has no Account");
		}
	}

	private static void requireExpenseOnly(EntryType type, Object value, String what) {
		if (type == EntryType.INCOME && value != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Income can't have " + what);
		}
	}

	/** Only an Expense can be a Contribution, and only to a Pot that exists. */
	private void requireValidPot(EntryType type, Long potId) {
		if (potId == null) {
			return;
		}
		if (type != EntryType.EXPENSE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only an Expense can pay into a Pot");
		}
		this.potService.requireExists(potId);
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
		PotMonth pots = this.potService.monthOf(month);
		return new MonthSummaryResponse(month, entries.stream().map((entry) -> EntryResponse.from(entry, month)).toList(), totalIncome,
				totalExpenses, totalIncome.subtract(totalExpenses), transfers(entries),
				expensesByCategory(entries), pots.pots(), pots.allocation());
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

	/** One Transfer per Account, always: the total of the Expenses paid out of it this month. */
	private static List<Transfer> transfers(List<Entry> entries) {
		return Arrays.stream(Account.values())
			.map((account) -> new Transfer(account,
					total(entries.stream().filter((entry) -> entry.getAccount() == account).toList(),
							EntryType.EXPENSE)))
			.toList();
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
