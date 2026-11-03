package com.pennypincher.backend.entry;

import java.time.YearMonth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PutMapping("/api/months/{month}/payments/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void pay(@PathVariable YearMonth month, @PathVariable Long entryId) {
		this.paymentService.pay(entryId, month);
	}

	@DeleteMapping("/api/months/{month}/payments/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unpay(@PathVariable YearMonth month, @PathVariable Long entryId) {
		this.paymentService.unpay(entryId, month);
	}

}
