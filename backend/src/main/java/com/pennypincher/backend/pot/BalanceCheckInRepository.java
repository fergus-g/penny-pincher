package com.pennypincher.backend.pot;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceCheckInRepository extends JpaRepository<BalanceCheckIn, Long> {

	List<BalanceCheckIn> findByPotIdOrderByMonth(Long potId);

	Optional<BalanceCheckIn> findByPotIdAndMonth(Long potId, YearMonth month);

	void deleteByPotId(Long potId);

}
