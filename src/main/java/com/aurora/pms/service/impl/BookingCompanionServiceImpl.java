package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateBookingCompanionRequest;
import com.aurora.pms.dto.request.UpdateBookingCompanionRequest;
import com.aurora.pms.dto.response.BookingCompanionResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.BookingCompanionMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.BookingCompanion;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.enums.GuestType;
import com.aurora.pms.repository.BookingCompanionRepository;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.service.BookingCompanionService;

@Service
public class BookingCompanionServiceImpl implements BookingCompanionService {

	private final BookingRepository bookingRepository;
	private final BookingCompanionRepository companionRepository;
	private final BookingCompanionMapper companionMapper;

	public BookingCompanionServiceImpl(
			BookingRepository bookingRepository,
			BookingCompanionRepository companionRepository,
			BookingCompanionMapper companionMapper
	) {
		this.bookingRepository = bookingRepository;
		this.companionRepository = companionRepository;
		this.companionMapper = companionMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookingCompanionResponse> findAllByBookingId(UUID bookingId) {
		ensureBookingExists(bookingId);
		return companionRepository.findByBookingIdOrderByCreatedAt(bookingId).stream()
				.map(companionMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public BookingCompanionResponse create(UUID bookingId, CreateBookingCompanionRequest request) {
		Booking booking = getBooking(bookingId);
		validateNotPrimaryGuest(booking, request.firstName(), request.lastName(), request.documentNumber());

		BookingCompanion companion = companionMapper.toEntity(request, booking);
		validateComposition(booking, companion, null);

		OffsetDateTime now = OffsetDateTime.now();
		companion.setCreatedAt(now);
		companion.setUpdatedAt(now);

		return companionMapper.toResponse(companionRepository.save(companion));
	}

	@Override
	@Transactional
	public BookingCompanionResponse update(UUID bookingId, UUID companionId, UpdateBookingCompanionRequest request) {
		Booking booking = getBooking(bookingId);
		BookingCompanion companion = getCompanionForBooking(bookingId, companionId);
		validateNotPrimaryGuest(booking, request.firstName(), request.lastName(), request.documentNumber());

		companionMapper.applyUpdate(companion, request);
		validateComposition(booking, companion, companionId);
		companion.setUpdatedAt(OffsetDateTime.now());

		return companionMapper.toResponse(companionRepository.save(companion));
	}

	@Override
	@Transactional
	public void delete(UUID bookingId, UUID companionId) {
		ensureBookingExists(bookingId);
		BookingCompanion companion = getCompanionForBooking(bookingId, companionId);
		companionRepository.delete(companion);
	}

	private void ensureBookingExists(UUID bookingId) {
		if (!bookingRepository.existsById(bookingId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
	}

	private Booking getBooking(UUID bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
	}

	private BookingCompanion getCompanionForBooking(UUID bookingId, UUID companionId) {
		return companionRepository.findByIdAndBookingId(companionId, bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking companion not found: " + companionId));
	}

	private void validateComposition(Booking booking, BookingCompanion candidate, UUID candidateId) {
		List<BookingCompanion> companions = companionRepository.findByBookingIdOrderByCreatedAt(booking.getId());
		long adults = 1;
		long children = 0;

		for (BookingCompanion companion : companions) {
			if (candidateId != null && companion.getId().equals(candidateId)) {
				continue;
			}
			if (companion.getGuestType() == GuestType.adult) {
				adults++;
			} else {
				children++;
			}
		}

		if (candidate.getGuestType() == GuestType.adult) {
			adults++;
		} else {
			children++;
		}

		long totalOccupants = adults + children;
		if (totalOccupants > booking.getRoomType().getCapacity()) {
			throw new BadRequestException("Booking companions exceed room type capacity");
		}
		if (adults > booking.getAdults()) {
			throw new BadRequestException("Booking companions exceed declared adult count");
		}
		if (children > booking.getChildren()) {
			throw new BadRequestException("Booking companions exceed declared child count");
		}
	}

	private void validateNotPrimaryGuest(Booking booking, String firstName, String lastName, String documentNumber) {
		Guest guest = booking.getGuest();
		String requestDocumentNumber = normalize(documentNumber);
		String guestDocumentNumber = normalize(guest.getDocumentNumber());
		if (requestDocumentNumber != null && guestDocumentNumber != null
				&& requestDocumentNumber.equalsIgnoreCase(guestDocumentNumber)) {
			throw new BadRequestException("Primary guest cannot be registered as a companion");
		}
		if (equalsIgnoreCase(firstName, guest.getFirstName()) && equalsIgnoreCase(lastName, guest.getLastName())) {
			throw new BadRequestException("Primary guest cannot be registered as a companion");
		}
	}

	private static boolean equalsIgnoreCase(String first, String second) {
		String normalizedFirst = normalize(first);
		String normalizedSecond = normalize(second);
		return normalizedFirst != null && normalizedSecond != null && normalizedFirst.equalsIgnoreCase(normalizedSecond);
	}

	private static String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
