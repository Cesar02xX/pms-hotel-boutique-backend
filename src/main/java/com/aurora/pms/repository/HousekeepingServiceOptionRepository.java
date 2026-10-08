package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.HousekeepingServiceOption;

public interface HousekeepingServiceOptionRepository extends JpaRepository<HousekeepingServiceOption, UUID> {
	List<HousekeepingServiceOption> findAllByOrderByNameAsc();
	List<HousekeepingServiceOption> findAllByActiveTrueOrderByNameAsc();
	boolean existsByNameIgnoreCase(String name);
	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
