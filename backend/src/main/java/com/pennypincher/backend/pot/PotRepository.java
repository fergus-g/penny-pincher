package com.pennypincher.backend.pot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PotRepository extends JpaRepository<Pot, Long> {

	List<Pot> findAllByOrderById();

}
