package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.SaveHousekeepingServiceRequest;
import com.aurora.pms.dto.response.HousekeepingServiceOptionResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.HousekeepingServiceOption;
import com.aurora.pms.repository.HousekeepingServiceOptionRepository;
import com.aurora.pms.service.HousekeepingCatalogService;

@Service
public class HousekeepingCatalogServiceImpl implements HousekeepingCatalogService {
	private final HousekeepingServiceOptionRepository repository;
	private final Clock clock;

	public HousekeepingCatalogServiceImpl(HousekeepingServiceOptionRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<HousekeepingServiceOptionResponse> findAll(boolean activeOnly) {
		return (activeOnly ? repository.findAllByActiveTrueOrderByNameAsc() : repository.findAllByOrderByNameAsc())
				.stream().map(HousekeepingCatalogServiceImpl::toResponse).toList();
	}

	@Override
	@Transactional
	public HousekeepingServiceOptionResponse create(SaveHousekeepingServiceRequest request) {
		String name = request.name().trim();
		if (repository.existsByNameIgnoreCase(name)) {
			throw new BadRequestException("A housekeeping service with this name already exists");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		HousekeepingServiceOption service = new HousekeepingServiceOption();
		service.setName(name);
		service.setDescription(trimToNull(request.description()));
		service.setActive(request.active() == null || request.active());
		service.setCreatedAt(now);
		service.setUpdatedAt(now);
		return toResponse(repository.save(service));
	}

	@Override
	@Transactional
	public HousekeepingServiceOptionResponse update(UUID id, SaveHousekeepingServiceRequest request) {
		HousekeepingServiceOption service = repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Housekeeping service not found: " + id));
		String name = request.name().trim();
		if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BadRequestException("A housekeeping service with this name already exists");
		}
		service.setName(name);
		service.setDescription(trimToNull(request.description()));
		if (request.active() != null) service.setActive(request.active());
		service.setUpdatedAt(OffsetDateTime.now(clock));
		return toResponse(repository.save(service));
	}

	private static HousekeepingServiceOptionResponse toResponse(HousekeepingServiceOption service) {
		return new HousekeepingServiceOptionResponse(service.getId(), service.getName(), service.getDescription(),
				Boolean.TRUE.equals(service.getActive()), service.getCreatedAt(), service.getUpdatedAt());
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
