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
import com.aurora.pms.dto.request.UpdateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.ConciergeRequestMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.ConciergeService;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.ConciergeServiceRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.ConciergeRequestService;
import com.aurora.pms.service.GuestNotificationService;

/**
 * Solo gestiona ServiceRequest de tipo concierge: una solicitud de otro tipo
 * se trata como inexistente (404). No genera cargos aunque exista charge_id.
 */
@Service
public class ConciergeRequestServiceImpl implements ConciergeRequestService {

	private static final ServiceRequestType TYPE = ServiceRequestType.concierge;

	private static final Set<BookingStatus> STATUSES_WITH_REQUESTS =
			EnumSet.of(BookingStatus.confirmed, BookingStatus.checked_in);

	/** Estados en los que alguien toma la solicitud: rechazar o cancelar no asigna responsable. */
	private static final Set<ServiceRequestStatus> STATUSES_ASSIGNING_ACTOR = EnumSet.of(
			ServiceRequestStatus.accepted,
			ServiceRequestStatus.in_progress,
			ServiceRequestStatus.completed
	);

	private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> TRANSITIONS = Map.of(
			ServiceRequestStatus.pending, EnumSet.of(ServiceRequestStatus.accepted, ServiceRequestStatus.rejected,
					ServiceRequestStatus.cancelled),
			ServiceRequestStatus.accepted, EnumSet.of(ServiceRequestStatus.in_progress, ServiceRequestStatus.cancelled),
			ServiceRequestStatus.in_progress, EnumSet.of(ServiceRequestStatus.completed, ServiceRequestStatus.cancelled),
			ServiceRequestStatus.completed, EnumSet.noneOf(ServiceRequestStatus.class),
			ServiceRequestStatus.rejected, EnumSet.noneOf(ServiceRequestStatus.class),
			ServiceRequestStatus.cancelled, EnumSet.noneOf(ServiceRequestStatus.class)
	);

	private final ServiceRequestRepository serviceRequestRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final ConciergeRequestMapper conciergeRequestMapper;
	private final Clock clock;
	private final GuestNotificationService guestNotificationService;
	private final ConciergeServiceRepository conciergeServiceRepository;

	public ConciergeRequestServiceImpl(
			ServiceRequestRepository serviceRequestRepository,
			BookingRepository bookingRepository,
			UserRepository userRepository,
			ConciergeRequestMapper conciergeRequestMapper,
			Clock clock,
			GuestNotificationService guestNotificationService,
			ConciergeServiceRepository conciergeServiceRepository
	) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.bookingRepository = bookingRepository;
		this.userRepository = userRepository;
		this.conciergeRequestMapper = conciergeRequestMapper;
		this.clock = clock;
		this.guestNotificationService = guestNotificationService;
		this.conciergeServiceRepository = conciergeServiceRepository;
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
		if (!STATUSES_WITH_REQUESTS.contains(booking.getStatus())) {
			throw new BadRequestException(
					"Cannot create concierge requests for a booking with status " + booking.getStatus());
		}
		ConciergeService selectedService = null;
		if (request.serviceId() != null) {
			selectedService = conciergeServiceRepository.findById(request.serviceId())
					.filter(service -> Boolean.TRUE.equals(service.getActive()))
					.orElseThrow(() -> new BadRequestException("Selected concierge service is unavailable"));
		}

		ServiceRequest serviceRequest = conciergeRequestMapper.toEntity(request, booking);
		if (selectedService != null) {
			serviceRequest.setConciergeService(selectedService);
			serviceRequest.setDescription(selectedService.getName());
		}
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
	public ConciergeRequestResponse update(UUID requestId, UpdateConciergeRequestRequest request) {
		ServiceRequest serviceRequest = serviceRequestRepository.findByIdAndTypeForUpdate(requestId, TYPE)
				.orElseThrow(() -> notFound(requestId));
		ServiceRequestStatus status = serviceRequest.getStatus();
		if (TRANSITIONS.get(status).isEmpty()) {
			throw new BadRequestException("Concierge request is already " + status);
		}
		// Las notas se pueden actualizar mientras se atiende; la descripcion solo antes de aceptar.
		if (request.description() != null && status != ServiceRequestStatus.pending) {
			throw new BadRequestException("Only pending concierge requests can be edited");
		}

		if (request.description() != null) {
			serviceRequest.setDescription(request.description().trim());
		}
		if (request.notes() != null) {
			serviceRequest.setNotes(trimToNull(request.notes()));
		}
		serviceRequest.setUpdatedAt(OffsetDateTime.now(clock));

		return conciergeRequestMapper.toResponse(serviceRequestRepository.save(serviceRequest));
	}

	@Override
	@Transactional
	public ConciergeRequestResponse updateStatus(UUID requestId, UpdateConciergeRequestStatusRequest request) {
		return updateStatus(requestId, request, null);
	}

	@Override
	@Transactional
	public ConciergeRequestResponse updateStatus(
			UUID requestId,
			UpdateConciergeRequestStatusRequest request,
			String actorEmail
	) {
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

		OffsetDateTime now = OffsetDateTime.now(clock);
		serviceRequest.setStatus(target);
		if (target == ServiceRequestStatus.in_progress && serviceRequest.getStartedAt() == null) {
			serviceRequest.setStartedAt(now);
		}
		if (target == ServiceRequestStatus.completed) {
			if (serviceRequest.getCompletedAt() == null) {
				serviceRequest.setCompletedAt(now);
			}
			if (actorEmail == null) {
				throw new BadRequestException("Authenticated user is required to complete a concierge request");
			}
			User completedBy = userRepository.findByEmail(actorEmail)
					.orElseThrow(() -> new BadRequestException("Authenticated user was not found"));
			serviceRequest.setCompletedByUser(completedBy);
		}
		if (request.responsibleUserId() != null) {
			serviceRequest.setResponsibleUser(findResponsibleUser(request.responsibleUserId()));
		} else if (serviceRequest.getResponsibleUser() == null
				&& actorEmail != null
				&& STATUSES_ASSIGNING_ACTOR.contains(target)) {
			userRepository.findByEmail(actorEmail).ifPresent(serviceRequest::setResponsibleUser);
		}
		serviceRequest.setNotes(appendNotes(serviceRequest.getNotes(), trimToNull(request.notes())));
		serviceRequest.setUpdatedAt(now);

		serviceRequest = serviceRequestRepository.save(serviceRequest);
		guestNotificationService.createIfAbsent(
				serviceRequest.getBooking(),
				"concierge_" + target,
				"Concierge",
				"Your concierge request is now " + target,
				"concierge_request",
				serviceRequest.getId()
		);
		return conciergeRequestMapper.toResponse(serviceRequest);
	}

	private User findResponsibleUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new BadRequestException("Responsible user not found: " + userId));
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
