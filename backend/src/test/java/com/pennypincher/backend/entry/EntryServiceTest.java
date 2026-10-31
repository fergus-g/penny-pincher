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
	void updateChangesFieldsAndCanMakeEntryOneOff() {
		Entry entry = entry(EntryType.EXPENSE, "1000.00");
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest(EntryType.EXPENSE, "Housing", "Rent", new BigDecimal("1100.00"), true));
		assertThat(response.category()).isEqualTo("Housing");
		assertThat(response.name()).isEqualTo("Rent");
		assertThat(response.amount()).isEqualByComparingTo("1100.00");
		assertThat(response.endMonth()).isEqualTo(OCT);
	}

	@Test
	void updateCanMakeOneOffEntryRecurring() {
		Entry entry = new Entry(EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), OCT, OCT);
		given(this.entryRepository.findById(1L)).willReturn(Optional.of(entry));
		EntryResponse response = this.entryService.update(1L,
				new UpdateEntryRequest(EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), false));
		assertThat(response.endMonth()).isNull();
	}

	@Test
	void updateMissingEntryThrowsNotFound() {
		given(this.entryRepository.findById(99L)).willReturn(Optional.empty());
		UpdateEntryRequest request = new UpdateEntryRequest(EntryType.EXPENSE, "X", null, BigDecimal.ONE, false);
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

	private static CreateEntryRequest createRequest(boolean oneOff) {
		return new CreateEntryRequest(EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), OCT, oneOff);
	}

	private static Entry entry(EntryType type, String amount) {
		return new Entry(type, "Test", null, new BigDecimal(amount), OCT, null);
	}

}
