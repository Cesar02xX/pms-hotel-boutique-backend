package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.UpdateGuestRequest;
import com.aurora.pms.dto.response.GuestResponse;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.GuestMapper;
import com.aurora.pms.model.Guest;
import com.aurora.pms.repository.GuestRepository;
import com.aurora.pms.service.GuestService;

@Service
public class GuestServiceImpl implements GuestService {

	private final GuestRepository guestRepository;
	private final GuestMapper guestMapper;

	public GuestServiceImpl(GuestRepository guestRepository, GuestMapper guestMapper) {
		this.guestRepository = guestRepository;
		this.guestMapper = guestMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<GuestResponse> findAll() {
		return guestRepository.findAll(Sort.by("lastName", "firstName")).stream()
				.map(guestMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public GuestResponse findById(UUID id) {
		return guestMapper.toResponse(getGuest(id));
	}

	@Override
	@Transactional
	public GuestResponse create(CreateGuestRequest request) {
		Guest guest = guestMapper.toEntity(request);
		OffsetDateTime now = OffsetDateTime.now();
		guest.setCreatedAt(now);
		guest.setUpdatedAt(now);

		return guestMapper.toResponse(guestRepository.save(guest));
	}

	@Override
	@Transactional
	public GuestResponse update(UUID id, UpdateGuestRequest request) {
		Guest guest = getGuest(id);
		OffsetDateTime createdAt = guest.getCreatedAt();

		guestMapper.applyUpdate(guest, request);
		guest.setCreatedAt(createdAt);
		guest.setUpdatedAt(OffsetDateTime.now());

		return guestMapper.toResponse(guestRepository.save(guest));
	}

	private Guest getGuest(UUID id) {
		return guestRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + id));
	}
}
