package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

	private final EntryService entryService = new EntryService(this.entryRepository);

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
	void transferCoversExpensesOnRevolut() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(entry(EntryType.INCOME, "2500.00"),
				entry(EntryType.EXPENSE, "1000.00"), entry(EntryType.EXPENSE, "200.00", Account.REVOLUT),
				entry(EntryType.EXPENSE, "100.00", Account.REVOLUT)));
		assertThat(this.entryService.getMonth(OCT).transfers()).singleElement().satisfies((transfer) -> {
			assertThat(transfer.account()).isEqualTo(Account.REVOLUT);
			assertThat(transfer.amount()).isEqualByComparingTo("300.00");
		});
	}

	@Test
	void transferIsNegativeWhenRevolutIncomeExceedsItsExpenses() {
		given(this.entryRepository.findActiveIn(OCT)).willReturn(List.of(
				entry(EntryType.INCOME, "500.00", Account.REVOLUT), entry(EntryType.EXPENSE, "120.00", Account.REVOLUT)));
		assertThat(this.entryService.getMonth(OCT).transfers().get(0).amount()).isEqualByComparingTo("-380.00");
	}

	@Test
	void transferIsZeroWithNoRevolutActivity() {
		given(this.entryRepository.findActiveIn(OCT))
			.willReturn(List.of(entry(EntryType.INCOME, "2500.00"), entry(EntryType.EXPENSE, "1000.00")));
		assertThat(this.entryService.getMonth(OCT).transfers()).singleElement().satisfies((transfer) -> {
			assertThat(transfer.account()).isEqualTo(Account.REVOLUT);
			assertThat(transfer.amount()).isZero();
		});
	}

	@Test
	void updateChangesFieldsAndCanMakeEntryOneOff() {
		Entry entry = entry(EntryType.EXPENSE, "1000.00");
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest("Housing", "Rent", new BigDecimal("1100.00"), Account.REVOLUT, true));
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
				new UpdateEntryRequest("Tech", "Laptop", new BigDecimal("300.50"), Account.AIB, false));
		assertThat(response.endMonth()).isNull();
	}

	@Test
	void updateMissingEntryThrowsNotFound() {
		given(this.entryRepository.findById(99L)).willReturn(Optional.empty());
		UpdateEntryRequest request = new UpdateEntryRequest("X", null, BigDecimal.ONE, Account.AIB, false);
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
				new Entry(EntryType.INCOME, "Salary", null, new BigDecimal("3200.00"), Account.AIB, OCT, null)));
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
				OCT, oneOff);
	}

	private static Entry entry(EntryType type, String amount) {
		return entry(type, amount, Account.AIB);
	}

	private static Entry entry(EntryType type, String amount, Account account) {
		return new Entry(type, "Test", null, new BigDecimal(amount), account, OCT, null);
	}

	private static Entry expense(String category, String amount) {
		return new Entry(EntryType.EXPENSE, category, null, new BigDecimal(amount), Account.AIB, OCT, null);
	}

}
