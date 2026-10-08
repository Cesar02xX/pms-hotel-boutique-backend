package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.SaveConciergeServiceRequest;
import com.aurora.pms.dto.response.ConciergeServiceResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.ConciergeService;
import com.aurora.pms.repository.ConciergeServiceRepository;

@Service
public class ConciergeCatalogServiceImpl implements com.aurora.pms.service.ConciergeCatalogService {
	private final ConciergeServiceRepository repository;
	private final Clock clock;

	public ConciergeCatalogServiceImpl(ConciergeServiceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ConciergeServiceResponse> findAll(boolean activeOnly) {
		return (activeOnly ? repository.findAllByActiveTrueOrderByNameAsc() : repository.findAllByOrderByNameAsc())
				.stream().map(ConciergeCatalogServiceImpl::toResponse).toList();
	}

	@Override
	@Transactional
	public ConciergeServiceResponse create(SaveConciergeServiceRequest request) {
		String name = request.name().trim();
		if (repository.existsByNameIgnoreCase(name)) throw new BadRequestException("A concierge service with this name already exists");
		OffsetDateTime now = OffsetDateTime.now(clock);
		ConciergeService service = new ConciergeService();
		service.setName(name);
		service.setDescription(trimToNull(request.description()));
		service.setActive(request.active() == null || request.active());
		service.setCreatedAt(now);
		service.setUpdatedAt(now);
		return toResponse(repository.save(service));
	}

	@Override
	@Transactional
	public ConciergeServiceResponse update(UUID id, SaveConciergeServiceRequest request) {
		ConciergeService service = repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Concierge service not found: " + id));
		String name = request.name().trim();
		if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) throw new BadRequestException("A concierge service with this name already exists");
		service.setName(name);
		service.setDescription(trimToNull(request.description()));
		if (request.active() != null) service.setActive(request.active());
		service.setUpdatedAt(OffsetDateTime.now(clock));
		return toResponse(repository.save(service));
	}

	private static ConciergeServiceResponse toResponse(ConciergeService service) {
		return new ConciergeServiceResponse(service.getId(), service.getName(), service.getDescription(),
				Boolean.TRUE.equals(service.getActive()), service.getCreatedAt(), service.getUpdatedAt());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}
}
