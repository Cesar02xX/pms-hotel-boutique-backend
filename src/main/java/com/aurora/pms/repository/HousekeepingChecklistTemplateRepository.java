package com.aurora.pms.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.HousekeepingChecklistTemplate;

public interface HousekeepingChecklistTemplateRepository
			extends JpaRepository<HousekeepingChecklistTemplate, UUID> {

	Optional<HousekeepingChecklistTemplate> findByCode(String code);
}
