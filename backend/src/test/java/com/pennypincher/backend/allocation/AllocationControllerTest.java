package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the HTTP layer of the Allocation endpoint only; the service is mocked.
 */
@WebMvcTest(AllocationController.class)
class AllocationControllerTest {

	private static final YearMonth MAR = YearMonth.of(2026, 3);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AllocationService allocationService;

	@Test
	void putSavesTheAllocationFromThatMonth() {
		given(this.allocationService.save(eq(MAR), any(AllocationRequest.class))).willReturn(new AllocationResponse(
				1L, MAR, new BigDecimal("400.00"), new BigDecimal("200.00"), 2L, null));
		assertThat(this.mvc.put()
			.uri("/api/months/2026-03/allocation")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"savings":400,"allowance":200,"savingsPotId":2}
					""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"effectiveFrom":"2026-03","savings":400.00,"savingsPotId":2,"debtPotId":null}
				""");
	}

	@Test
	void negativeSavingsReturns400() {
		assertThat(this.mvc.put()
			.uri("/api/months/2026-03/allocation")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"savings":-1,"allowance":200}
					""")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"errors":{"savings":"must be greater than or equal to 0"}}
				""");
		verifyNoInteractions(this.allocationService);
	}

}
