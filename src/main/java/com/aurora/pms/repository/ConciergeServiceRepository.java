package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.ConciergeService;

public interface ConciergeServiceRepository extends JpaRepository<ConciergeService, UUID> {
	List<ConciergeService> findAllByOrderByNameAsc();
	List<ConciergeService> findAllByActiveTrueOrderByNameAsc();
	boolean existsByNameIgnoreCase(String name);
	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
