package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.allocation.AllocationSummary;
import com.pennypincher.backend.pot.PotMonth;
import com.pennypincher.backend.pot.PotService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EntryService}, with the repository mocked. Month filtering
 * itself is the repository query's job, covered in {@link EntryRepositoryTest}.
 */
class EntryServiceTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);

	private final EntryRepository entryRepository = mock(EntryRepository.class);

	private final PotService potService = mock(PotService.class);

	private final EntryService entryService = new EntryService(this.entryRepository, this.potService);

	/** No Pots and no Allocation unless a test says otherwise. */
	@BeforeEach
	void noPots() {
		given(this.potService.monthOf(any(YearMonth.class))).willReturn(new PotMonth(List.of(), null));
	}

	@Test
	void createOneOffEndsInItsStartMonth() {
		given(this.entryRepository.save(any(Entry.class))).willAnswer((invocation) -> invocation.getArgument(0));
		EntryResponse response = this.entryService.create(createRequest(true));
		assertThat(response.startMonth()).isEqualTo(OCT);
		assertThat(response.endMonth()).isEqualTo(OCT);
	}

	@Test
	void createRecurringHasNoEndMonth() {
		given(this.entryRepository.save(any(Entry.class))).willAnswer((invocation) -> invocation.getArgument(0));
		EntryResponse response = this.entryService.create(createRequest(false));
		assertThat(response.endMonth()).isNull();
		assertThat(response.account()).isEqualTo(Account.REVOLUT);
	}

	@Test
	void createExpenseCanPayIntoAnExistingPot() {
		given(this.entryRepository.save(any(Entry.class))).willAnswer((invocation) -> invocation.getArgument(0));
		EntryResponse response = this.entryService.create(new CreateEntryRequest(EntryType.EXPENSE, "Loans", null,
				new BigDecimal("250.00"), Account.AIB, OCT, false, 7L));
		verify(this.potService).requireExists(7L);
		assertThat(response.potId()).isEqualTo(7L);
	}

	@Test
	void incomeCannotPayIntoAPot() {
		CreateEntryRequest request = new CreateEntryRequest(EntryType.INCOME, "Salary", null, BigDecimal.TEN, null,
				OCT, false, 7L);
		assertBadRequest(() -> this.entryService.create(request));
	}

	@Test
	void updateCanUnlinkAContribution() {
		Entry entry = entry(EntryType.EXPENSE, "250.00");
		entry.setPotId(7L);
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest("Loans", null, new BigDecimal("250.00"), Account.AIB, false, null));
		assertThat(response.potId()).isNull();
	}

	@Test
	void getMonthCalculatesTotalsAndRemainder() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(entry(EntryType.INCOME, "2500.00"),
				entry(EntryType.EXPENSE, "1000.00"), entry(EntryType.EXPENSE, "300.50")));
		MonthSummaryResponse summary = this.entryService.getMonth(OCT);
		assertThat(summary.month()).isEqualTo(OCT);
		assertThat(summary.entries()).hasSize(3);
		assertThat(summary.totalIncome()).isEqualByComparingTo("2500.00");
		assertThat(summary.totalExpenses()).isEqualByComparingTo("1300.50");
		assertThat(summary.remainder()).isEqualByComparingTo("1199.50");
	}

	@Test
	void getMonthWithExpensesOverIncomeHasNegativeRemainder() {
		given(this.entryRepository.findActiveIn(OCT))
			.willReturn(List.of(entry(EntryType.INCOME, "100.00"), entry(EntryType.EXPENSE, "150.25")));
		assertThat(this.entryService.getMonth(OCT).remainder()).isEqualByComparingTo("-50.25");
	}

	@Test
	void getMonthWithNoEntriesIsAllZero() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of());
		MonthSummaryResponse summary = this.entryService.getMonth(OCT);
		assertThat(summary.entries()).isEmpty();
		assertThat(summary.totalIncome()).isZero();
		assertThat(summary.totalExpenses()).isZero();
		assertThat(summary.remainder()).isZero();
	}

	@Test
	void transfersPutEachAccountsExpensesIntoIt() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(entry(EntryType.INCOME, "2500.00"),
				entry(EntryType.EXPENSE, "1000.00"), entry(EntryType.EXPENSE, "200.00", Account.REVOLUT),
				entry(EntryType.EXPENSE, "100.00", Account.REVOLUT)));
		List<Transfer> transfers = this.entryService.getMonth(OCT).transfers();
		assertThat(transfers).extracting(Transfer::account).containsExactly(Account.AIB, Account.REVOLUT);
		assertThat(transfers.get(0).amount()).isEqualByComparingTo("1000.00");
		assertThat(transfers.get(1).amount()).isEqualByComparingTo("300.00");
	}

	@Test
	void anAllocationDoesNotChangeTransfers() {
		given(this.entryRepository.findActiveIn(OCT))
			.willReturn(List.of(entry(EntryType.INCOME, "2500.00"), entry(EntryType.EXPENSE, "100.00", Account.REVOLUT)));
		List<Transfer> without = this.entryService.getMonth(OCT).transfers();
		given(this.potService.monthOf(OCT)).willReturn(new PotMonth(List.of(),
				new AllocationSummary(OCT, new BigDecimal("400.00"), new BigDecimal("200.00"), new BigDecimal("1800.00"),
						false, 1L, "Rainy day", 2L, "Car loan", BigDecimal.ZERO, null)));
		assertThat(this.entryService.getMonth(OCT).transfers()).isEqualTo(without);
	}

	@Test
	void transfersListEveryAccountEvenWithNothingPaidOutOfIt() {
		given(this.entryRepository.findActiveIn(OCT))
			.willReturn(List.of(entry(EntryType.INCOME, "2500.00"), entry(EntryType.EXPENSE, "1000.00")));
		List<Transfer> transfers = this.entryService.getMonth(OCT).transfers();
		assertThat(transfers).extracting(Transfer::account).containsExactly(Account.AIB, Account.REVOLUT);
		assertThat(transfers.get(1).amount()).isZero();
	}

	@Test
	void incomeCannotHaveAnAccount() {
		CreateEntryRequest request = new CreateEntryRequest(EntryType.INCOME, "Salary", null, BigDecimal.TEN,
				Account.AIB, OCT, false, null);
		assertBadRequest(() -> this.entryService.create(request));
	}

	@Test
	void anExpenseNeedsAnAccount() {
		CreateEntryRequest request = new CreateEntryRequest(EntryType.EXPENSE, "Rent", null, BigDecimal.TEN, null,
				OCT, false, null);
		assertBadRequest(() -> this.entryService.create(request));
	}

	@Test
	void incomeIsCreatedWithoutAnAccount() {
		given(this.entryRepository.save(any(Entry.class))).willAnswer((invocation) -> invocation.getArgument(0));
		EntryResponse response = this.entryService.create(new CreateEntryRequest(EntryType.INCOME, "Salary", null,
				BigDecimal.TEN, null, OCT, false, null));
		assertThat(response.account()).isNull();
	}

	@Test
	void updateChangesFieldsAndCanMakeEntryOneOff() {
		Entry entry = entry(EntryType.EXPENSE, "1000.00");
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest("Housing", "Rent", new BigDecimal("1100.00"), Account.REVOLUT, true, null));
		assertThat(response.type()).isEqualTo(EntryType.EXPENSE);
		assertThat(response.category()).isEqualTo("Housing");
		assertThat(response.name()).isEqualTo("Rent");
		assertThat(response.amount()).isEqualByComparingTo("1100.00");
		assertThat(response.account()).isEqualTo(Account.REVOLUT);
		assertThat(response.endMonth()).isEqualTo(OCT);
	}

	@Test
	void updateCanMakeOneOffEntryRecurring() {
		Entry entry = new Entry(EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), Account.AIB, OCT, OCT);
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest("Tech", "Laptop", new BigDecimal("300.50"), Account.AIB, false, null));
		assertThat(response.endMonth()).isNull();
	}

	@Test
	void updateMissingEntryThrowsNotFound() {
		given(this.entryRepository.findById(99L)).willReturn(Optional.empty());
		UpdateEntryRequest request = new UpdateEntryRequest("X", null, BigDecimal.ONE, Account.AIB, false, null);
		assertThatThrownBy(() -> this.entryService.update(99L, request)).isInstanceOfSatisfying(
				ResponseStatusException.class, (ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void deleteRemovesExistingEntry() {
		Entry entry = entry(EntryType.EXPENSE, "10.00");
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		this.entryService.delete(1L);
		verify(this.entryRepository).delete(entry);
	}

	@Test
	void deleteMissingEntryThrowsNotFoundAndDeletesNothing() {
		given(this.entryRepository.findById(99L)).willReturn(Optional.empty());
		assertThatThrownBy(() -> this.entryService.delete(99L)).isInstanceOfSatisfying(ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		verify(this.entryRepository, never()).delete(any(Entry.class));
	}

	@Test
	void expensesByCategorySumToTotalExpensesLargestFirst() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(expense("Housing", "1250.00"),
				expense("Groceries", "300.00"), expense("Transport", "90.50"), expense("Groceries", "120.00"),
				new Entry(EntryType.INCOME, "Salary", null, new BigDecimal("3200.00"), null, OCT, null)));
		MonthSummaryResponse summary = this.entryService.getMonth(OCT);
		assertThat(summary.expensesByCategory()).extracting(CategoryTotal::category)
			.containsExactly("Housing", "Groceries", "Transport");
		assertThat(summary.expensesByCategory().stream().map(CategoryTotal::amount).reduce(BigDecimal::add))
			.hasValueSatisfying((sum) -> assertThat(sum).isEqualByComparingTo(summary.totalExpenses()));
	}

	@Test
	void categoryVariantsShareASliceLabelledWithTheMostCommonSpelling() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(expense("groceries ", "10.00"),
				expense("Groceries", "20.00"), expense(" Groceries", "30.00"), expense("GROCERIES", "40.00")));
		assertThat(this.entryService.getMonth(OCT).expensesByCategory()).singleElement().satisfies((slice) -> {
			assertThat(slice.category()).isEqualTo("Groceries");
			assertThat(slice.amount()).isEqualByComparingTo("100.00");
		});
	}

	@Test
	void getCategoriesMergesVariantsAlphabetically() {
		given(this.entryRepository.findCategories(EntryType.EXPENSE))
			.willReturn(List.of("transport", "Groceries", "groceries ", "Groceries", "Housing", "Transport "));
		assertThat(this.entryService.getCategories(EntryType.EXPENSE)).containsExactly("Groceries", "Housing",
				"transport");
	}

	private static CreateEntryRequest createRequest(boolean oneOff) {
		return new CreateEntryRequest(EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), Account.REVOLUT,
				OCT, oneOff, null);
	}

	private void assertBadRequest(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
				(ex) -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(this.entryRepository, never()).save(any(Entry.class));
	}

	/** Income gets no Account; an Expense is on AIB. */
	private static Entry entry(EntryType type, String amount) {
		return entry(type, amount, (type == EntryType.EXPENSE) ? Account.AIB : null);
	}

	private static Entry entry(EntryType type, String amount, Account account) {
		return new Entry(type, "Test", null, new BigDecimal(amount), account, OCT, null);
	}

	private static Entry expense(String category, String amount) {
		return new Entry(EntryType.EXPENSE, category, null, new BigDecimal(amount), Account.AIB, OCT, null);
	}

}
