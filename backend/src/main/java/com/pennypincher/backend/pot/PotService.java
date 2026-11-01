package com.pennypincher.backend.pot;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.allocation.AllocationRepository;
import com.pennypincher.backend.entry.EntryRepository;

@Service
public class PotService {

	private final PotRepository potRepository;

	private final BalanceCheckInRepository checkInRepository;

	private final EntryRepository entryRepository;

	private final AllocationRepository allocationRepository;

	public PotService(PotRepository potRepository, BalanceCheckInRepository checkInRepository,
			EntryRepository entryRepository, AllocationRepository allocationRepository) {
		this.potRepository = potRepository;
		this.checkInRepository = checkInRepository;
		this.entryRepository = entryRepository;
		this.allocationRepository = allocationRepository;
	}

	/** Every Pot, archived ones included. */
	@Transactional(readOnly = true)
	public List<PotResponse> list() {
		return this.potRepository.findAllByOrderById().stream().map(PotResponse::from).toList();
	}

	@Transactional
	public PotResponse create(CreatePotRequest request) {
		Pot pot = new Pot(request.name(), request.kind(), request.account(), request.openingBalance(),
				request.openingMonth());
		return PotResponse.from(this.potRepository.save(pot));
	}

	@Transactional
	public PotResponse update(Long id, UpdatePotRequest request) {
		Pot pot = findPot(id);
		if (request.archivedFrom() != null && request.archivedFrom().isBefore(request.openingMonth())) {
			throw badRequest("A Pot can't be archived before its opening month");
		}
		List<BalanceCheckIn> checkIns = this.checkInRepository.findByPotIdOrderByMonth(id);
		if (!checkIns.isEmpty() && checkIns.get(0).getMonth().isBefore(request.openingMonth())) {
			throw badRequest("Pot " + id + " has a Balance Check-in before " + request.openingMonth());
		}
		pot.setName(request.name());
		pot.setAccount(request.account());
		pot.setOpeningBalance(request.openingBalance());
		pot.setOpeningMonth(request.openingMonth());
		pot.setArchivedFrom(request.archivedFrom());
		return PotResponse.from(pot);
	}

	@Transactional(readOnly = true)
	public List<CheckInResponse> checkIns(Long potId) {
		findPot(potId);
		return this.checkInRepository.findByPotIdOrderByMonth(potId).stream().map(CheckInResponse::from).toList();
	}

	/** Records the Pot's actual balance at the end of {@code month}, replacing any earlier Check-in for it. */
	@Transactional
	public CheckInResponse checkIn(Long potId, YearMonth month, CheckInRequest request) {
		Pot pot = findPot(potId);
		if (month.isBefore(pot.getOpeningMonth())) {
			throw badRequest("Pot " + potId + " opens in " + pot.getOpeningMonth());
		}
		BalanceCheckIn checkIn = this.checkInRepository.findByPotIdAndMonth(potId, month)
			.orElseGet(() -> new BalanceCheckIn(potId, month, request.balance()));
		checkIn.setBalance(request.balance());
		return CheckInResponse.from(this.checkInRepository.save(checkIn));
	}

	@Transactional
	public void deleteCheckIn(Long potId, YearMonth month) {
		BalanceCheckIn checkIn = this.checkInRepository.findByPotIdAndMonth(potId, month)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Pot " + potId + " has no Balance Check-in for " + month));
		this.checkInRepository.delete(checkIn);
	}

	/** Each Pot in use in {@code month} with its end-of-month balance, and the month's Allocation. */
	@Transactional(readOnly = true)
	public PotMonth monthOf(YearMonth month) {
		return MonthLedger.run(month, this.potRepository.findAllByOrderById(), this.checkInRepository.findAll(),
				this.entryRepository.findAll(), this.allocationRepository.findAllByOrderByEffectiveFrom());
	}

	/** Rejects a Contribution to a Pot that doesn't exist. */
	@Transactional(readOnly = true)
	public void requireExists(Long potId) {
		if (!this.potRepository.existsById(potId)) {
			throw badRequest("Pot " + potId + " not found");
		}
	}

	private Pot findPot(Long id) {
		return this.potRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pot " + id + " not found"));
	}

	private static ResponseStatusException badRequest(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}

}
