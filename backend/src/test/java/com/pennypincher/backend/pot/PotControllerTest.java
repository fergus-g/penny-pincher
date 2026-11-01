package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.entry.Account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the HTTP layer of the Pot endpoints only; the service is mocked.
 */
@WebMvcTest(PotController.class)
class PotControllerTest {

	private static final YearMonth JAN = YearMonth.of(2026, 1);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private PotService potService;

	@Test
	void createReturns201WithPot() {
		given(this.potService.create(any(CreatePotRequest.class))).willReturn(new PotResponse(1L, "Car loan",
				PotKind.LOAN, Account.AIB, new BigDecimal("5000.00"), JAN, null));
		assertThat(this.mvc.post().uri("/api/pots").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Car loan","kind":"LOAN","account":"AIB","openingBalance":5000,"openingMonth":"2026-01"}
				""")).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"id":1,"kind":"LOAN","openingBalance":5000.00,"openingMonth":"2026-01","archivedFrom":null}
				""");
	}

	@Test
	void createWithNegativeOpeningBalanceReturns400() {
		assertThat(this.mvc.post().uri("/api/pots").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Car loan","kind":"LOAN","account":"AIB","openingBalance":-1,"openingMonth":"2026-01"}
				""")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"errors":{"openingBalance":"must be greater than or equal to 0"}}
				""");
		verifyNoInteractions(this.potService);
	}

	@Test
	void deletingAPotWithContributionsReturns409() {
		willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Pot 1 has Contributions; archive it instead"))
			.given(this.potService)
			.delete(1L);
		assertThat(this.mvc.delete().uri("/api/pots/1")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status":409,"detail":"Pot 1 has Contributions; archive it instead"}
					""");
	}

	@Test
	void checkInPutsTheMonthsBalance() {
		given(this.potService.checkIn(eq(1L), eq(YearMonth.of(2026, 3)), any(CheckInRequest.class)))
			.willReturn(new CheckInResponse(YearMonth.of(2026, 3), new BigDecimal("4000.00")));
		assertThat(this.mvc.put()
			.uri("/api/pots/1/check-ins/2026-03")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"balance":4000}
					""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"month":"2026-03","balance":4000.00}
				""");
	}

}
