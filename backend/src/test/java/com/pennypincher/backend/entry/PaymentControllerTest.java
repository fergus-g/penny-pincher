package com.pennypincher.backend.entry;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/** Tests the HTTP layer only; the service is mocked. */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

	private static final YearMonth NOV = YearMonth.of(2026, 11);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private PaymentService paymentService;

	@Test
	void putTicksAndReturns204() {
		assertThat(this.mvc.put().uri("/api/months/2026-11/payments/1")).hasStatus(HttpStatus.NO_CONTENT);
		verify(this.paymentService).pay(1L, NOV);
	}

	@Test
	void deleteUnticksAndReturns204() {
		assertThat(this.mvc.delete().uri("/api/months/2026-11/payments/1")).hasStatus(HttpStatus.NO_CONTENT);
		verify(this.paymentService).unpay(1L, NOV);
	}

	@Test
	void aRejectedTickIs400() {
		willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only AIB Expenses can be ticked as paid"))
			.given(this.paymentService)
			.pay(2L, NOV);
		assertThat(this.mvc.put().uri("/api/months/2026-11/payments/2")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
