package com.pennypincher.backend.entry;

import java.time.YearMonth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryController {

	private final EntryService entryService;

	public EntryController(EntryService entryService) {
		this.entryService = entryService;
	}

	@PostMapping("/api/entries")
	@ResponseStatus(HttpStatus.CREATED)
	public EntryResponse create(@Valid @RequestBody CreateEntryRequest request) {
		return this.entryService.create(request);
	}

	@GetMapping("/api/months/{month}")
	public MonthSummaryResponse getMonth(@PathVariable YearMonth month) {
		return this.entryService.getMonth(month);
	}

}
