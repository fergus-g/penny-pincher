package com.pennypincher.backend.allocation;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

	List<Allocation> findAllByOrderByEffectiveFrom();

	/** The Allocation in force in {@code month}: the latest one effective on or before it. */
	Optional<Allocation> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(YearMonth month);

}
