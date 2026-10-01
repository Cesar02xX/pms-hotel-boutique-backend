package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.UpdateGuestRequest;
import com.aurora.pms.dto.response.GuestResponse;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.GuestMapper;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.enums.DocumentType;
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

		validateUniqueness(guest, null);
		return guestMapper.toResponse(saveGuest(guest));
	}

	@Override
	@Transactional
	public GuestResponse update(UUID id, UpdateGuestRequest request) {
		Guest guest = getGuest(id);
		OffsetDateTime createdAt = guest.getCreatedAt();

		// Validate on a detached copy so the uniqueness queries do not auto-flush pending changes.
		Guest candidate = new Guest();
		guestMapper.applyUpdate(candidate, request);
		validateUniqueness(candidate, id);

		guestMapper.applyUpdate(guest, request);
		guest.setCreatedAt(createdAt);
		guest.setUpdatedAt(OffsetDateTime.now());

		return guestMapper.toResponse(saveGuest(guest));
	}

	private void validateUniqueness(Guest guest, UUID currentId) {
		String email = guest.getEmail();
		if (email != null) {
			boolean emailTaken = currentId == null
					? guestRepository.existsByEmailIgnoreCase(email)
					: guestRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
			if (emailTaken) {
				throw new ConflictException("Guest email already exists: " + email);
			}
		}

		DocumentType documentType = guest.getDocumentType();
		String documentNumber = guest.getDocumentNumber();
		if (documentType != null && documentNumber != null) {
			boolean documentTaken = currentId == null
					? guestRepository.existsByDocumentTypeAndDocumentNumber(documentType, documentNumber)
					: guestRepository.existsByDocumentTypeAndDocumentNumberAndIdNot(documentType, documentNumber, currentId);
			if (documentTaken) {
				throw new ConflictException("Guest document already exists: " + documentType + " " + documentNumber);
			}
		}
	}

	private Guest saveGuest(Guest guest) {
		try {
			return guestRepository.saveAndFlush(guest);
		} catch (DataIntegrityViolationException exception) {
			// Concurrent requests can pass the checks above; the unique indexes are the final guard.
			throw new ConflictException("Guest email or document already exists");
		}
	}

	private Guest getGuest(UUID id) {
		return guestRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + id));
	}
}
