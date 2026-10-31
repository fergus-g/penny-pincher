package com.pennypincher.backend.entry;

import java.time.YearMonth;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<Entry, Long> {

	@Query("select e from Entry e where e.startMonth <= :month"
			+ " and (e.endMonth is null or e.endMonth >= :month) order by e.id")
	List<Entry> findActiveIn(YearMonth month);

}
