package com.pennypincher.backend.allocation;

import java.time.YearMonth;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AllocationController {

	private final AllocationService allocationService;

	public AllocationController(AllocationService allocationService) {
		this.allocationService = allocationService;
	}

	@PutMapping("/api/months/{month}/allocation")
	public AllocationResponse save(@PathVariable YearMonth month, @Valid @RequestBody AllocationRequest request) {
		return this.allocationService.save(month, request);
	}

}
