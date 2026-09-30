package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.ConciergeRequestMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.service.ConciergeRequestService;

/**
 * Solo gestiona ServiceRequest de tipo concierge: una solicitud de otro tipo
 * se trata como inexistente (404). No genera cargos aunque exista charge_id.
 */
@Service
public class ConciergeRequestServiceImpl implements ConciergeRequestService {

	private static final ServiceRequestType TYPE = ServiceRequestType.concierge;

	private static final Set<BookingStatus> STATUSES_WITHOUT_REQUESTS =
			EnumSet.of(BookingStatus.checked_out, BookingStatus.cancelled, BookingStatus.no_show);

	/** accepted -> rejected se permite: una solicitud aceptada puede resultar imposible de cumplir. */
	private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> TRANSITIONS = Map.of(
			ServiceRequestStatus.pending, EnumSet.of(ServiceRequestStatus.accepted, ServiceRequestStatus.rejected),
			ServiceRequestStatus.accepted, EnumSet.of(ServiceRequestStatus.in_progress, ServiceRequestStatus.rejected),
			ServiceRequestStatus.in_progress, EnumSet.of(ServiceRequestStatus.completed),
			ServiceRequestStatus.completed, EnumSet.noneOf(ServiceRequestStatus.class),
			ServiceRequestStatus.rejected, EnumSet.noneOf(ServiceRequestStatus.class)
	);

	private final ServiceRequestRepository serviceRequestRepository;
	private final BookingRepository bookingRepository;
	private final ConciergeRequestMapper conciergeRequestMapper;
	private final Clock clock;

	public ConciergeRequestServiceImpl(
			ServiceRequestRepository serviceRequestRepository,
			BookingRepository bookingRepository,
			ConciergeRequestMapper conciergeRequestMapper,
			Clock clock
	) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.bookingRepository = bookingRepository;
		this.conciergeRequestMapper = conciergeRequestMapper;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ConciergeRequestResponse> findAll(UUID bookingId, ServiceRequestStatus status) {
		return serviceRequestRepository.search(TYPE, bookingId, status).stream()
				.map(conciergeRequestMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ConciergeRequestResponse findById(UUID requestId) {
		ServiceRequest request = serviceRequestRepository.findByIdAndType(requestId, TYPE)
				.orElseThrow(() -> notFound(requestId));
		return conciergeRequestMapper.toResponse(request);
	}

	@Override
	@Transactional
	public ConciergeRequestResponse create(CreateConciergeRequestRequest request) {
		Booking booking = bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new BadRequestException("Booking not found: " + request.bookingId()));
		if (STATUSES_WITHOUT_REQUESTS.contains(booking.getStatus())) {
			throw new BadRequestException(
					"Cannot create concierge requests for a booking with status " + booking.getStatus());
		}

		ServiceRequest serviceRequest = conciergeRequestMapper.toEntity(request, booking);
		OffsetDateTime now = OffsetDateTime.now(clock);
		serviceRequest.setType(TYPE);
		serviceRequest.setStatus(ServiceRequestStatus.pending);
		serviceRequest.setRequestedAt(now);
		serviceRequest.setCreatedAt(now);
		serviceRequest.setUpdatedAt(now);

		return conciergeRequestMapper.toResponse(serviceRequestRepository.save(serviceRequest));
	}

	@Override
	@Transactional
	public ConciergeRequestResponse updateStatus(UUID requestId, UpdateConciergeRequestStatusRequest request) {
		// El bloqueo evita que dos cambios simultáneos salten el flujo de estados.
		ServiceRequest serviceRequest = serviceRequestRepository.findByIdAndTypeForUpdate(requestId, TYPE)
				.orElseThrow(() -> notFound(requestId));

		ServiceRequestStatus current = serviceRequest.getStatus();
		ServiceRequestStatus target = request.status();
		if (TRANSITIONS.getOrDefault(current, Set.of()).isEmpty()) {
			throw new BadRequestException("Concierge request is already " + current);
		}
		if (!TRANSITIONS.get(current).contains(target)) {
			throw new BadRequestException("Invalid status transition from " + current + " to " + target);
		}

		serviceRequest.setStatus(target);
		serviceRequest.setNotes(appendNotes(serviceRequest.getNotes(), trimToNull(request.notes())));
		serviceRequest.setUpdatedAt(OffsetDateTime.now(clock));

		return conciergeRequestMapper.toResponse(serviceRequestRepository.save(serviceRequest));
	}

	private static ResourceNotFoundException notFound(UUID requestId) {
		return new ResourceNotFoundException("Concierge request not found: " + requestId);
	}

	private static String appendNotes(String current, String addition) {
		if (addition == null) {
			return current;
		}
		return current == null ? addition : current + "\n" + addition;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
