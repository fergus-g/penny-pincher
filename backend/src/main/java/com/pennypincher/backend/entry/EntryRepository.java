package com.pennypincher.backend.entry;

import java.time.YearMonth;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<Entry, Long> {

	@Query("select e from Entry e where e.startMonth <= :month"
			+ " and (e.endMonth is null or e.endMonth >= :month) order by e.id")
	List<Entry> findActiveIn(YearMonth month);

	/** Every Entry's category of this type, one per Entry, so repeats show how common a spelling is. */
	@Query("select e.category from Entry e where e.type = :type")
	List<String> findCategories(EntryType type);

}
