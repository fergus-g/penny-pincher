package com.pennypincher.backend.entry;

import java.time.YearMonth;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByMonth(YearMonth month);

	boolean existsByEntryIdAndMonth(Long entryId, YearMonth month);

	void deleteByEntryIdAndMonth(Long entryId, YearMonth month);

}
