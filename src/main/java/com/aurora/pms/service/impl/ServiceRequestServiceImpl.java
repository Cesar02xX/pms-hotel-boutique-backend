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

import com.aurora.pms.dto.request.CreateServiceRequestRequest;
import com.aurora.pms.dto.request.UpdateServiceRequestStatusRequest;
import com.aurora.pms.dto.response.ServiceRequestResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.ServiceRequestMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.ServiceRequestService;

@Service
public class ServiceRequestServiceImpl implements ServiceRequestService {

	private static final Set<BookingStatus> STATUSES_WITH_REQUESTS =
			EnumSet.of(BookingStatus.confirmed, BookingStatus.checked_in);

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
	private final RoomRepository roomRepository;
	private final UserRepository userRepository;
	private final ServiceRequestMapper serviceRequestMapper;
	private final Clock clock;

	public ServiceRequestServiceImpl(
			ServiceRequestRepository serviceRequestRepository,
			BookingRepository bookingRepository,
			RoomRepository roomRepository,
			UserRepository userRepository,
			ServiceRequestMapper serviceRequestMapper,
			Clock clock
	) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.bookingRepository = bookingRepository;
		this.roomRepository = roomRepository;
		this.userRepository = userRepository;
		this.serviceRequestMapper = serviceRequestMapper;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ServiceRequestResponse> findAll(ServiceRequestType type, UUID bookingId, UUID roomId,
			ServiceRequestStatus status) {
		return serviceRequestRepository.searchGeneral(type, bookingId, roomId, status).stream()
				.map(serviceRequestMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ServiceRequestResponse findById(UUID id) {
		return serviceRequestMapper.toResponse(serviceRequestRepository.findById(id)
				.orElseThrow(() -> notFound(id)));
	}

	@Override
	@Transactional
	public ServiceRequestResponse create(CreateServiceRequestRequest request) {
		Room room = roomRepository.findById(request.roomId())
				.orElseThrow(() -> new BadRequestException("Room not found: " + request.roomId()));
		Booking booking = request.bookingId() == null ? null : bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new BadRequestException("Booking not found: " + request.bookingId()));
		if (booking != null) {
			validateBookingAllowsRequests(booking);
			validateRoomMatchesBooking(request.roomId(), booking);
		}
		if (request.type() == ServiceRequestType.concierge || request.type() == ServiceRequestType.housekeeping) {
			throw dedicatedEndpoint(request.type());
		}

		ServiceRequest serviceRequest = serviceRequestMapper.toEntity(request, booking, room);
		OffsetDateTime now = OffsetDateTime.now(clock);
		serviceRequest.setStatus(ServiceRequestStatus.pending);
		serviceRequest.setRequestedAt(now);
		serviceRequest.setCreatedAt(now);
		serviceRequest.setUpdatedAt(now);

		return serviceRequestMapper.toResponse(serviceRequestRepository.save(serviceRequest));
	}

	@Override
	@Transactional
	public ServiceRequestResponse updateStatus(UUID id, UpdateServiceRequestStatusRequest request, String actorEmail) {
		ServiceRequest serviceRequest = serviceRequestRepository.findByIdForUpdate(id)
				.orElseThrow(() -> notFound(id));
		if (serviceRequest.getType() == ServiceRequestType.concierge
				|| serviceRequest.getType() == ServiceRequestType.housekeeping) {
			throw dedicatedEndpoint(serviceRequest.getType());
		}

		ServiceRequestStatus current = serviceRequest.getStatus();
		ServiceRequestStatus target = request.status();
		if (TRANSITIONS.getOrDefault(current, Set.of()).isEmpty()) {
			throw new BadRequestException("Service request is already " + current);
		}
		if (!TRANSITIONS.get(current).contains(target)) {
			throw new BadRequestException("Invalid status transition from " + current + " to " + target);
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		serviceRequest.setStatus(target);
		if (target == ServiceRequestStatus.in_progress && serviceRequest.getStartedAt() == null) {
			serviceRequest.setStartedAt(now);
		}
		if (target == ServiceRequestStatus.completed && serviceRequest.getCompletedAt() == null) {
			serviceRequest.setCompletedAt(now);
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

		return serviceRequestMapper.toResponse(serviceRequestRepository.save(serviceRequest));
	}

	private void validateBookingAllowsRequests(Booking booking) {
		if (!STATUSES_WITH_REQUESTS.contains(booking.getStatus())) {
			throw new BadRequestException(
					"Cannot create service requests for a booking with status " + booking.getStatus());
		}
	}

	private static void validateRoomMatchesBooking(UUID roomId, Booking booking) {
		if (booking.getRoom() == null || !booking.getRoom().getId().equals(roomId)) {
			throw new BadRequestException("Room does not belong to booking: " + booking.getId());
		}
	}

	private User findResponsibleUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new BadRequestException("Responsible user not found: " + userId));
	}

	private static ResourceNotFoundException notFound(UUID id) {
		return new ResourceNotFoundException("Service request not found: " + id);
	}

	private static BadRequestException dedicatedEndpoint(ServiceRequestType type) {
		return new BadRequestException("Use the dedicated endpoint for " + type + " requests");
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
