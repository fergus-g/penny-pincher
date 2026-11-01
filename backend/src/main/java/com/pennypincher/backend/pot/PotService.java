package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pennypincher.backend.entry.Entry;
import com.pennypincher.backend.entry.EntryRepository;

@Service
public class PotService {

	private final PotRepository potRepository;

	private final BalanceCheckInRepository checkInRepository;

	private final EntryRepository entryRepository;

	public PotService(PotRepository potRepository, BalanceCheckInRepository checkInRepository,
			EntryRepository entryRepository) {
		this.potRepository = potRepository;
		this.checkInRepository = checkInRepository;
		this.entryRepository = entryRepository;
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

	/** Only a Pot with no Contributions can go; otherwise archive it. Its Check-ins go with it. */
	@Transactional
	public void delete(Long id) {
		Pot pot = findPot(id);
		if (this.entryRepository.existsByPotId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Pot " + id + " has Contributions; archive it instead");
		}
		this.checkInRepository.deleteByPotId(id);
		this.potRepository.delete(pot);
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

	/** Each Pot in use in {@code month}, with its balance at the end of that month. */
	@Transactional(readOnly = true)
	public List<PotBalance> balancesIn(YearMonth month) {
		return this.potRepository.findAllByOrderById()
			.stream()
			.filter((pot) -> pot.isActiveIn(month))
			.map((pot) -> balanceIn(pot, month, this.checkInRepository.findByPotIdOrderByMonth(pot.getId()),
					this.entryRepository.findByPotId(pot.getId())))
			.toList();
	}

	/** Rejects a Contribution to a Pot that doesn't exist. */
	@Transactional(readOnly = true)
	public void requireExists(Long potId) {
		if (!this.potRepository.existsById(potId)) {
			throw badRequest("Pot " + potId + " not found");
		}
	}

	/**
	 * Start from the latest Check-in on or before {@code month}, or else the opening balance, both
	 * end-of-month figures. Then apply each later month's Contributions up to {@code month}: they
	 * add to a Savings Pot and pay down a Debt Pot, which never goes below zero.
	 */
	static PotBalance balanceIn(Pot pot, YearMonth month, List<BalanceCheckIn> checkIns,
			List<Entry> contributions) {
		Optional<BalanceCheckIn> latest = checkIns.stream()
			.filter((checkIn) -> !checkIn.getMonth().isAfter(month))
			.max(Comparator.comparing(BalanceCheckIn::getMonth));
		YearMonth from = latest.map(BalanceCheckIn::getMonth).orElse(pot.getOpeningMonth());
		BigDecimal balance = latest.map(BalanceCheckIn::getBalance).orElse(pot.getOpeningBalance());
		for (YearMonth next = from.plusMonths(1); !next.isAfter(month); next = next.plusMonths(1)) {
			YearMonth current = next;
			BigDecimal paid = contributions.stream()
				.filter((entry) -> entry.isActiveIn(current))
				.map(Entry::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			balance = pot.getKind().isDebt() ? balance.subtract(paid).max(BigDecimal.ZERO) : balance.add(paid);
		}
		boolean checkedIn = latest.isPresent() && latest.get().getMonth().equals(month);
		return new PotBalance(pot.getId(), pot.getName(), pot.getKind(), pot.getAccount(), balance, checkedIn);
	}

	private Pot findPot(Long id) {
		return this.potRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pot " + id + " not found"));
	}

	private static ResponseStatusException badRequest(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}

}
