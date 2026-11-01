package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the HTTP layer only: routing, JSON, status codes, validation and the error
 * responses from {@code GlobalExceptionHandler}. The service is mocked.
 */
@WebMvcTest(EntryController.class)
class EntryControllerTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private EntryService entryService;

	@Test
	void createReturns201WithEntry() {
		given(this.entryService.create(any(CreateEntryRequest.class))).willReturn(laptop());
		assertThat(this.mvc.post()
			.uri("/api/entries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"type":"EXPENSE","category":"Tech","name":"Laptop","amount":300.50,"account":"REVOLUT","startMonth":"2026-10","oneOff":true}
					"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id":1,"name":"Laptop","account":"REVOLUT","startMonth":"2026-10","endMonth":"2026-10"}
					""");
	}

	@Test
	void createWithInvalidBodyReturns400WithFieldErrors() {
		assertThat(this.mvc.post()
			.uri("/api/entries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"type":"EXPENSE","category":" ","amount":-5,"startMonth":"2026-10","dueDay":32}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"errors":{"category":"must not be blank","amount":"must be greater than 0",
					"dueDay":"must be less than or equal to 31"}}
					""");
		verifyNoInteractions(this.entryService);
	}

	@Test
	void getMonthReturnsSummary() {
		given(this.entryService.getMonth(OCT)).willReturn(new MonthSummaryResponse(OCT, List.of(laptop()),
				BigDecimal.ZERO, new BigDecimal("300.50"), new BigDecimal("-300.50"),
				List.of(new Transfer(Account.AIB, BigDecimal.ZERO), new Transfer(Account.REVOLUT, new BigDecimal("300.50"))),
				List.of(new CategoryTotal("Tech", new BigDecimal("300.50"))), List.of(), null));
		assertThat(this.mvc.get().uri("/api/months/2026-10")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"month":"2026-10","entries":[{"id":1}],"totalExpenses":300.50,"remainder":-300.50,"transfers":[{"account":"AIB","amount":0},{"account":"REVOLUT","amount":300.50}],
				"expensesByCategory":[{"category":"Tech","amount":300.50}]}
				""");
	}

	@Test
	void getCategoriesReturnsTheTypesCategories() {
		given(this.entryService.getCategories(EntryType.EXPENSE)).willReturn(List.of("Groceries", "Housing"));
		assertThat(this.mvc.get().uri("/api/categories?type=EXPENSE")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				["Groceries","Housing"]
				""");
	}

	@Test
	void getCategoriesWithoutTypeReturns400() {
		assertThat(this.mvc.get().uri("/api/categories")).hasStatus(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(this.entryService);
	}

	@Test
	void updateWithoutTypeReturns200WithEntry() {
		given(this.entryService.update(eq(1L), any(UpdateEntryRequest.class))).willReturn(laptop());
		assertThat(this.mvc.put()
			.uri("/api/entries/1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"category":"Tech","name":"Laptop","amount":300.50,"account":"REVOLUT","oneOff":true}
					"""))
			.hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id":1,"type":"EXPENSE","name":"Laptop"}
					""");
	}

	@Test
	void updateMissingEntryReturns404() {
		given(this.entryService.update(eq(99L), any(UpdateEntryRequest.class)))
			.willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry 99 not found"));
		assertThat(this.mvc.put()
			.uri("/api/entries/99")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"category":"Tech","amount":1,"account":"AIB"}
					"""))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status":404,"detail":"Entry 99 not found"}
					""");
	}

	@Test
	void deleteReturns204() {
		assertThat(this.mvc.delete().uri("/api/entries/1")).hasStatus(HttpStatus.NO_CONTENT);
		verify(this.entryService).delete(1L);
	}

	private static EntryResponse laptop() {
		return new EntryResponse(1L, EntryType.EXPENSE, "Tech", "Laptop", new BigDecimal("300.50"), null, null, Account.REVOLUT,
				OCT, OCT, null, 31, 31, Instant.parse("2026-10-31T21:30:43Z"));
	}

}
