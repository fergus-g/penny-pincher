package com.pennypincher.backend.pot;

import java.time.YearMonth;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PotController {

	private final PotService potService;

	public PotController(PotService potService) {
		this.potService = potService;
	}

	@GetMapping("/api/pots")
	public List<PotResponse> list() {
		return this.potService.list();
	}

	@PostMapping("/api/pots")
	@ResponseStatus(HttpStatus.CREATED)
	public PotResponse create(@Valid @RequestBody CreatePotRequest request) {
		return this.potService.create(request);
	}

	@PutMapping("/api/pots/{id}")
	public PotResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePotRequest request) {
		return this.potService.update(id, request);
	}

	@DeleteMapping("/api/pots/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		this.potService.delete(id);
	}

	@GetMapping("/api/pots/{id}/check-ins")
	public List<CheckInResponse> checkIns(@PathVariable Long id) {
		return this.potService.checkIns(id);
	}

	@PutMapping("/api/pots/{id}/check-ins/{month}")
	public CheckInResponse checkIn(@PathVariable Long id, @PathVariable YearMonth month,
			@Valid @RequestBody CheckInRequest request) {
		return this.potService.checkIn(id, month, request);
	}

	@DeleteMapping("/api/pots/{id}/check-ins/{month}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCheckIn(@PathVariable Long id, @PathVariable YearMonth month) {
		this.potService.deleteCheckIn(id, month);
	}

}
